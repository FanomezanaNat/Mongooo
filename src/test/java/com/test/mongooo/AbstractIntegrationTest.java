package com.test.mongooo;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.test.mongooo.config.database.configuration.FlamingockAuditConfig;
import com.test.mongooo.config.database.connection.MongoConfigConnection;
import de.flapdoodle.commons.net.Net;
import de.flapdoodle.commons.reverse.StateID;
import de.flapdoodle.commons.reverse.TransitionWalker;
import de.flapdoodle.commons.reverse.transitions.Start;
import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.commands.MongosArguments;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.distribution.Version.Main;
import de.flapdoodle.embed.mongo.transitions.*;
import de.flapdoodle.embed.process.io.ProcessOutput;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@FlamingockSpringBootTest
@ActiveProfiles("test")
@Import({MongoConfigConnection.class, FlamingockAuditConfig.class})
@TestInstance(PER_CLASS)
public abstract class AbstractIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

  protected static final String HOST = "127.0.0.1";
  protected static final String DB_NAME = "mongooo_test";
  protected static final String COLLECTION = "users";

  private static final String CONFIG_RS = "configReplSet";
  private static final String SHARD_1_RS = "shardReplSet1";
  private static final String SHARD_2_RS = "shardReplSet2";

  private static volatile TransitionWalker.ReachedState<RunningMongosProcess> mongosProcess;

  // Holds a live reference to every started mongod node so the processes
  // aren't eligible for GC mid-suite (which can tear them down unexpectedly
  // and produce flaky "failed to elect a PRIMARY" failures) and so they can
  // be stopped cleanly in stopCluster().
  private static final List<TransitionWalker.ReachedState<RunningMongodProcess>> mongodProcesses =
      new CopyOnWriteArrayList<>();

  @DynamicPropertySource
  static void configureMongoProperties(DynamicPropertyRegistry registry) {
    TransitionWalker.ReachedState<RunningMongosProcess> process = startCluster();

    String mongoUri =
        "mongodb://" + HOST + ":" + process.current().getServerAddress().getPort() + "/" + DB_NAME;
    registry.add("spring.data.mongodb.uri", () -> mongoUri);
    registry.add("spring.data.mongodb.database", () -> DB_NAME);
  }

  @AfterAll
  static void stopCluster() {
    if (mongosProcess != null) {
      mongosProcess.close();
      mongosProcess = null;
    }
    mongodProcesses.forEach(TransitionWalker.ReachedState::close);
    mongodProcesses.clear();
  }

  private static synchronized TransitionWalker.ReachedState<RunningMongosProcess> startCluster() {
    if (mongosProcess != null) {
      return mongosProcess;
    }

    try {
      int cfgPort = Net.freeServerPort();
      int s1Port = Net.freeServerPort();
      int s2Port = Net.freeServerPort();
      int mongosPort = Net.freeServerPort();

      // 1. Démarrage du Config Server & des Shards
      startMongodNode(cfgPort, CONFIG_RS,
          MongodArguments.defaults().withReplication(Storage.of(CONFIG_RS, 100)).withIsConfigServer(true), true);
      startMongodNode(s1Port, SHARD_1_RS,
          MongodArguments.defaults().withReplication(Storage.of(SHARD_1_RS, 100)).withIsShardServer(true), false);
      startMongodNode(s2Port, SHARD_2_RS,
          MongodArguments.defaults().withReplication(Storage.of(SHARD_2_RS, 100)).withIsShardServer(true), false);

      // 2. Démarrage du Routeur Mongos
      //
      // BUGFIX: MongosArguments requires the config server's replica set name
      // as a *separate* field from configDB — configDB takes only host:port,
      // not "replSetName/host:port". Folding both into withConfigDB() left
      // the required replicaSet field unset, causing:
      //   IllegalArgumentException: you must define a replicaSet
      // (see flapdoodle-oss/de.flapdoodle.embed.mongo docs/Howto.md, sharded
      // cluster example: .withConfigDB(serverAddress.toString()).withReplicaSet(name))
      mongosProcess = Mongos.instance().transitions(Main.V7_0)
          .replace(Start.to(de.flapdoodle.embed.mongo.config.Net.class)
              .initializedWith(de.flapdoodle.embed.mongo.config.Net.of(HOST, mongosPort, false)))
          .replace(Start.to(MongosArguments.class)
              .initializedWith(MongosArguments.defaults()
                  .withConfigDB(HOST + ":" + cfgPort)
                  .withReplicaSet(CONFIG_RS)))
          .replace(Start.to(ProcessOutput.class)
              .initializedWith(ProcessOutput.silent()))
          .walker()
          .initState(StateID.of(RunningMongosProcess.class));

      // 3. Enregistrement des Shards via le client Mongos
      try (MongoClient client = MongoClients.create("mongodb://" + HOST + ":" + mongosPort)) {
        var admin = client.getDatabase("admin");
        admin.runCommand(new Document("addShard", SHARD_1_RS + "/" + HOST + ":" + s1Port));
        admin.runCommand(new Document("addShard", SHARD_2_RS + "/" + HOST + ":" + s2Port));
        admin.runCommand(new Document("enableSharding", DB_NAME));

        client.getDatabase(DB_NAME).createCollection(COLLECTION);
        client.getDatabase(DB_NAME).getCollection(COLLECTION).createIndex(new Document("userId", 1));
        admin.runCommand(new Document("shardCollection", DB_NAME + "." + COLLECTION).append("key", new Document("userId", 1)));
      }

      return mongosProcess;

    } catch (Exception e) {
      // Best-effort cleanup of whatever did come up before failing the suite,
      // so a failed startCluster() doesn't still leak processes.
      stopCluster();
      throw new IllegalStateException("Failed to start embedded MongoDB sharded cluster", e);
    }
  }

  private static void startMongodNode(int port, String replicaSetName, MongodArguments args, boolean isConfigServer)
      throws Exception {
    // BUGFIX: this previously targeted StateID.of(RunningMongosProcess.class),
    // which is the mongos router's state type, not mongod's. A Mongod
    // transition graph has no route to RunningMongosProcess, so this would
    // throw at runtime. The correct target state for a mongod walker is
    // RunningMongodProcess.
    TransitionWalker.ReachedState<RunningMongodProcess> running =
        Mongod.instance().transitions(Main.V7_0)
            .replace(Start.to(de.flapdoodle.embed.mongo.config.Net.class)
                .initializedWith(de.flapdoodle.embed.mongo.config.Net.of(HOST, port, false)))
            .replace(Start.to(MongodArguments.class)
                .initializedWith(args))
            .replace(Start.to(ProcessOutput.class)
                .initializedWith(ProcessOutput.silent()))
            .walker()
            .initState(StateID.of(RunningMongodProcess.class));

    // BUGFIX: previously the reached state was discarded entirely, leaving
    // nothing to prevent GC of the running process and no way to stop it later.
    mongodProcesses.add(running);

    // directConnection=true évite que le driver exige le paramètre replicaSet avant l'initialisation
    String directUri = "mongodb://" + HOST + ":" + port + "/admin?directConnection=true";

    try (MongoClient client = MongoClients.create(directUri)) {
      Document config = new Document("_id", replicaSetName)
          .append("members", List.of(new Document("_id", 0).append("host", HOST + ":" + port)));

      if (isConfigServer) {
        config.append("configsvr", true);
      }

      try {
        client.getDatabase("admin").runCommand(new Document("replSetInitiate", config));
      } catch (Exception e) {
        // Ignore si déjà initialisé, mais on logue quand même pour ne pas
        // masquer une vraie erreur de configuration derrière un simple
        // "failed to elect a PRIMARY" 15 secondes plus tard.
        log.debug("replSetInitiate on port {} raised (likely already initialized): {}", port, e.getMessage());
      }

      // Attente active jusqu'à l'élection du rôle PRIMARY
      boolean isPrimary = false;
      for (int i = 0; i < 60; i++) {
        try {
          Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
          if (Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"))) {
            isPrimary = true;
            break;
          }
        } catch (Exception ignored) {
          // Ignore les coupures temporaires de socket pendant le changement d'état du cluster
        }
        TimeUnit.MILLISECONDS.sleep(250);
      }

      if (!isPrimary) {
        throw new IllegalStateException("Replica Set '" + replicaSetName + "' on port " + port + " failed to elect a PRIMARY node.");
      }
    }
  }
}