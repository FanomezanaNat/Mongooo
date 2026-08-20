package com.bank.dbs.cqrs;

import org.bson.BsonDocument;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Persists the Change Stream resume token so the listener can resume exactly where
 * it left off after a pod restart, without missing or duplicating events (AC-DB-07).
 * Seeded with _id='docs_listener' by V003ChangeStreamSetup.
 */
@Document(collection = "change_stream_tokens")
public class ChangeStreamToken {

    @Id
    private String id; // "docs_listener"

    @Field("resumeToken")
    private BsonDocument resumeToken;

    public ChangeStreamToken() {
    }

    public ChangeStreamToken(String id, BsonDocument resumeToken) {
        this.id = id;
        this.resumeToken = resumeToken;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BsonDocument getResumeToken() {
        return resumeToken;
    }

    public void setResumeToken(BsonDocument resumeToken) {
        this.resumeToken = resumeToken;
    }
}
