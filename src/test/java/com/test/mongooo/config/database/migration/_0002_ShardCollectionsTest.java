package com.test.mongooo.config.database.migration;

import static io.flamingock.support.domain.AuditEntryDefinition.APPLIED;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.test.mongooo.AbstractIntegrationTest;
import com.test.mongooo.config.database.schema.MongoSchema;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTestSupport;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class _0002_ShardCollectionsTest extends AbstractIntegrationTest {

  @Autowired
  private FlamingockSpringBootTestSupport testSupport;
  @Autowired
  private MongoTemplate mongoTemplate;
  @Autowired
  private MongoClient mongoClient;

  @BeforeEach
  void cleanDatabase() {
    if (mongoTemplate != null) {
      mongoTemplate.getDb().drop();
    }
  }

  @Test
  @DisplayName("Should successfully execute collection sharding and hashed index creation")
  void shouldExecuteShardCollections() {
    testSupport.givenBuilderFromContext()
        // NOTE: no andExistingAudit() here. andExistingAudit() only writes a
        // fake audit marker saying a change "already ran" — it does NOT
        // execute that change's actual code. V002's sharding/index work
        // operates on collections that V001 itself creates, so faking V001
        // as already-applied left `docs` never actually created, which is
        // exactly why getIndexInfo() came back empty in the previous run.
        // Against a freshly-dropped DB, the correct thing is to let both
        // changes execute for real (which is what naturally happens) and
        // assert on the actual resulting audit sequence below.
        .whenRun()
        .thenExpectAuditFinalStateSequence(
            APPLIED(_0001__CreateUsersIndex.class),
            APPLIED(_0002__ShardCollections.class))
        .verify();

    assertHashedIndexPresent(MongoSchema.Collections.DOCS, MongoSchema.Fields.CUSTOMER_ID);
    assertHashedIndexPresent(MongoSchema.Collections.APPLICATION_DOCS,
        MongoSchema.Fields.APPLICATION_ID);
    assertHashedIndexPresent(MongoSchema.Collections.TASKS, "_id");

    String targetDbName = mongoTemplate.getDb().getName();
    assertCollectionIsSharded(targetDbName, MongoSchema.Collections.DOCS);
    assertCollectionIsSharded(targetDbName, MongoSchema.Collections.APPLICATION_DOCS);
    assertCollectionIsSharded(targetDbName, MongoSchema.Collections.TASKS);
  }

  @Test
  @DisplayName("Should skip migration if it has already been executed previously")
  void shouldSkipAlreadyAppliedShardCollections() {
    testSupport.givenBuilderFromContext()
        // Unlike the test above, this one is fine to seed both as
        // already-applied: it only asserts on audit behaviour (the "skip if
        // already applied" path), and makes no schema assertions that
        // depend on the changes' real side effects having run.
        .andExistingAudit(
            APPLIED(_0001__CreateUsersIndex.class),
            APPLIED(_0002__ShardCollections.class))
        .whenRun()
        .thenExpectAuditFinalStateSequence(
            APPLIED(_0001__CreateUsersIndex.class),
            APPLIED(_0002__ShardCollections.class))
        .verify();
  }

  private void assertHashedIndexPresent(String collectionName, String fieldName) {
    var indexOps = mongoTemplate.indexOps(collectionName);
    var indexes = indexOps.getIndexInfo();
    assertThat(indexes).as("Hashed index on '" + fieldName + "' in collection '" + collectionName
            + "' should be present")
        .anyMatch(index -> index.isIndexForFields(List.of(fieldName)));
  }

  private void assertCollectionIsSharded(String dbName, String collectionName) {
    String collectionNamespace = dbName + "." + collectionName;
    Document collectionConfig = mongoClient.getDatabase("config")
        .getCollection("collections")
        .find(new Document("_id", collectionNamespace))
        .first();
    assertThat(collectionConfig).as(
            "Collection '" + collectionNamespace + "' should be registered in config.collections")
        .isNotNull();
    boolean isDropped = collectionConfig.getBoolean("dropped", false);
    assertThat(isDropped).as("Collection '" + collectionNamespace
            + "' should not be marked as dropped in config.collections")
        .isFalse();
  }
}