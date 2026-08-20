package com.bank.dbs.migration;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables sharding on the `dbs` database and shards docs / application_docs / tasks
 * per spec 6.4:
 *   - docs               -> {customerId: 'hashed'}
 *   - application_docs   -> {applicationId: 'hashed'}
 *   - tasks              -> {_id: 'hashed'}
 *
 * signed_urls and doc_locks are intentionally left unsharded (low volume /
 * TTL-managed / single-primary lock atomicity requirement).
 *
 * Idempotent (AC-DB-01): "already enabled" / "already sharded" MongoCommandException
 * codes are treated as success so re-running V002 is safe. In lower environments
 * running a standalone/single-shard MongoDB (local dev, CI with Flapdoodle), these
 * admin commands typically fail outright — we log and continue rather than fail the
 * whole migration run, since MongoStartupValidator is the authoritative guard
 * against running the full production stack on an unsupported topology.
 */
@ChangeUnit(id = "V002ShardCollections", order = "002", author = "dbs-team")
public class V002ShardCollections {

    private static final Logger log = LoggerFactory.getLogger(V002ShardCollections.class);

    // Mongock resolves driver-native types (MongoClient, MongoDatabase) as
    // injectable parameters on the @Execution method.
    @Execution
    public void execution(MongoClient mongoClient, MongoDatabase db) {
        String dbName = db.getName();
        MongoDatabase admin = mongoClient.getDatabase("admin");

        runAdminCommandIdempotent(admin, new Document("enableSharding", dbName));
        runAdminCommandIdempotent(admin,
                new Document("shardCollection", dbName + ".docs").append("key", new Document("customerId", "hashed")));
        runAdminCommandIdempotent(admin,
                new Document("shardCollection", dbName + ".application_docs").append("key", new Document("applicationId", "hashed")));
        runAdminCommandIdempotent(admin,
                new Document("shardCollection", dbName + ".tasks").append("key", new Document("_id", "hashed")));
    }

    @RollbackExecution
    public void rollback(MongoClient mongoClient, MongoDatabase db) {
        // Un-sharding a collection is not a supported MongoDB operation; forward-only.
    }

    private void runAdminCommandIdempotent(MongoDatabase admin, Document command) {
        try {
            admin.runCommand(command);
            log.info("Executed sharding admin command: {}", command.toJson());
        } catch (MongoCommandException e) {
            if (isAlreadyDoneOrUnsupported(e)) {
                log.info("Sharding command already applied or not applicable, skipping: {}", command.toJson());
            } else {
                throw e;
            }
        }
    }

    private boolean isAlreadyDoneOrUnsupported(MongoCommandException e) {
        String msg = e.getErrorMessage() == null ? "" : e.getErrorMessage().toLowerCase();
        return msg.contains("already") // "sharding already enabled", "already sharded"
                || msg.contains("not running with sharding") // single-node dev/test topology
                || msg.contains("no shards found");
    }
}
