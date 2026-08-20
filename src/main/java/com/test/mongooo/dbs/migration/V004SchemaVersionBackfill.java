package com.bank.dbs.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backfills schemaVersion=1 on any pre-existing docs that predate the introduction
 * of the Spring Data {@code @Version} field, so updateMetadata()'s optimistic-lock
 * retry logic (AC-BE-13) never encounters a null version. Purely additive and
 * idempotent — updateMany's filter excludes documents that already have the field,
 * so re-running is a no-op (AC-DB-01).
 */
@ChangeUnit(id = "V004SchemaVersionBackfill", order = "004", author = "dbs-team")
public class V004SchemaVersionBackfill {

    private static final Logger log = LoggerFactory.getLogger(V004SchemaVersionBackfill.class);

    @Execution
    public void execution(MongoDatabase db) {
        MongoCollection<Document> docs = db.getCollection("docs");

        UpdateResult result = docs.updateMany(
                new Document("schemaVersion", new Document("$exists", false)),
                new Document("$set", new Document("schemaVersion", 1L)));

        log.info("V004SchemaVersionBackfill: set schemaVersion=1 on {} pre-existing docs",
                result.getModifiedCount());
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        // Removing schemaVersion would break optimistic locking for any doc updated
        // since this migration ran; forward-only.
    }
}
