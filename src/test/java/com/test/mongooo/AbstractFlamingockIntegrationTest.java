package com.test.mongooo;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.test.mongooo.config.database.configuration.FlamingockAuditConfig;
import com.test.mongooo.config.database.connection.MongoConfigConnection;
import de.flapdoodle.commons.reverse.StateID;
import de.flapdoodle.commons.reverse.TransitionWalker;
import de.flapdoodle.commons.reverse.transitions.Start;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version.Main;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTest;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@FlamingockSpringBootTest
@ActiveProfiles("test")
@Import({MongoConfigConnection.class, FlamingockAuditConfig.class})
@TestInstance(PER_CLASS)
public class AbstractFlamingockIntegrationTest {

  private static TransitionWalker.ReachedState<RunningMongodProcess> runningState;
  private static RunningMongodProcess mongoProcess;

  private static synchronized RunningMongodProcess getOrStartMongoProcess() {
    if (mongoProcess == null) {
      runningState = Mongod.builder()
          .net(Start.to(Net.class)
              .initializedWith(Net.defaults()))
          .processOutput(Start.to(ProcessOutput.class)
              .initializedWith(ProcessOutput.silent()))
          .build()
          .transitions(Main.V8_3)
          .walker()
          .initState(StateID.of(RunningMongodProcess.class));

      mongoProcess = runningState.current();
      Runtime.getRuntime()
          .addShutdownHook(new Thread(() -> {
            if (runningState != null) {
              runningState.close();
            }
          }));
    }
    return mongoProcess;
  }

  @DynamicPropertySource
  static void setMongoProperties(DynamicPropertyRegistry registry) {
    RunningMongodProcess running = getOrStartMongoProcess();

    String uri = "mongodb://" + running.getServerAddress()
        .getHost()
        + ":" + running.getServerAddress()
        .getPort();

    registry.add("spring.data.mongodb.uri", () -> uri);
    registry.add("spring.data.mongodb.database", () -> "mongooo_test");
  }
}
