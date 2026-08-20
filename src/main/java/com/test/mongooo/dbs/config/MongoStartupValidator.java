package com.test.mongooo.X.config;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AC-BE-15 / AC-DB-08: refuses application startup if the target MongoDB deployment
 * is a standalone mongod rather than a replica set. DBS depends on multi-document
 * transactions (VersionService), Change Streams (CQRS), and majority write concern —
 * none of which are available against a standalone instance.
 *
 * We check by running {@code hello} (formerly isMaster) against the admin database
 * and inspecting the presence of a `setName` field, which is only populated for
 * replica-set members.
 */
@Component
public class MongoStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(MongoStartupValidator.class);

    private final MongoClient mongoClient;

    public MongoStartupValidator(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @PostConstruct
    public void validate() {
        Document hello = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));

        boolean isReplicaSetMember = hello.containsKey("setName");
        if (!isReplicaSetMember) {
            throw new IllegalStateException(
                    "DBS requires MongoDB to be running as a replica set (multi-doc transactions, "
                            + "Change Streams, and majority write concern all depend on it). "
                            + "The connected instance returned no replica set 'setName' in its hello/isMaster "
                            + "response, indicating a standalone mongod. Refusing to start. "
                            + "For local dev, run: mongod --replSet rs0 && rs.initiate().");
        }

        log.info("MongoDB replica set '{}' confirmed at startup.", hello.getString("setName"));
    }
}
