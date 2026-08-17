package com.test.mongooo.config.database.migration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@TargetSystem(id = "mongodb-target")
@Change(id = "V002ShardCollections", author = "fanomezana")
public class _0002__ShardCollections {

  private final MongoClient mongoClient;
  private final MongoTemplate mongoTemplate;

  public _0002__ShardCollections(MongoClient mongoClient, MongoTemplate mongoTemplate) {
    this.mongoClient = mongoClient;
    this.mongoTemplate = mongoTemplate;
  }

  @Apply
  public void apply() {
    MongoDatabase adminDb = mongoClient.getDatabase("admin");
    MongoDatabase targetDb = mongoTemplate.getDb();
    var dbName = targetDb.getName();

    // 1. Activer le sharding sur la base "dbs" si ce n'est pas déjà fait
    enableShardingIfNeeded(adminDb,dbName );

    // 2. Sharder les collections (Création de l'index 'hashed' + shardCollection)
    shardCollectionIfNeeded(adminDb, targetDb, "docs", "customerId");
    shardCollectionIfNeeded(adminDb, targetDb, "application_docs", "applicationId");
    shardCollectionIfNeeded(adminDb, targetDb, "tasks", "_id");
  }

  @Rollback
  public void rollback() {
  }

  private void shardCollectionIfNeeded(MongoDatabase adminDb, MongoDatabase targetDb,
      String collectionName, String shardKey) {
    String collectionNamespace = targetDb.getName() + "." + collectionName;

    targetDb.getCollection(collectionName)
        .createIndex(new Document(shardKey, "hashed"));

    Document collectionConfig = mongoClient.getDatabase("config")
        .getCollection("collections")
        .find(new Document("_id", collectionNamespace))
        .first();

    boolean isAlreadySharded =
        collectionConfig != null && !collectionConfig.getBoolean("dropped", false);

    if (!isAlreadySharded) {
      adminDb.runCommand(new Document("shardCollection", collectionNamespace).append("key",
          new Document(shardKey, "hashed")));
    }
  }


  private void enableShardingIfNeeded(MongoDatabase adminDb, String dbName) {
    Document configDbDoc = mongoClient.getDatabase("config")
        .getCollection("databases")
        .find(new Document("_id", dbName))
        .first();

    boolean isSharded =
        configDbDoc != null && Boolean.TRUE.equals(configDbDoc.getBoolean("partitioned"));

    if (!isSharded) {
      adminDb.runCommand(new Document("enableSharding", dbName));
    }

  }
}
