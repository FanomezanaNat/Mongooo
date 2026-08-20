package com.bank.dbs.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;

/**
 * Seeds the change_stream_tokens/docs_listener placeholder document (so
 * ChangeStreamListenerService's first-ever resume-token lookup finds a row rather
 * than needing to special-case "no document yet"), and creates the docs_view
 * collection with its compound indexes for the CQRS read model — spec 6.5.
 */
@ChangeUnit(id = "V003ChangeStreamSetup", order = "003", author = "dbs-team")
public class V003ChangeStreamSetup {

    @Execution
    public void execution(MongoDatabase db) {
        MongoCollection<Document> tokens = db.getCollection("change_stream_tokens");
        Document placeholder = new Document("_id", "docs_listener").append("resumeToken", null);
        tokens.replaceOne(new Document("_id", "docs_listener"), placeholder, new ReplaceOptions().upsert(true));

        // Ensure docs_view exists even before the first change event arrives.
        if (!collectionExists(db, "docs_view")) {
            db.createCollection("docs_view");
        }

        MongoCollection<Document> docsView = db.getCollection("docs_view");
        docsView.createIndex(Indexes.ascending("customerId", "docType", "isCurrent"),
                new IndexOptions().name("idx_view_customer_type_current"));
        docsView.createIndex(Indexes.descending("dtCreated"),
                new IndexOptions().name("idx_view_dtCreated"));
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        // Forward-only: dropping docs_view would break live dashboard reads.
    }

    private boolean collectionExists(MongoDatabase db, String name) {
        for (String existing : db.listCollectionNames()) {
            if (existing.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
