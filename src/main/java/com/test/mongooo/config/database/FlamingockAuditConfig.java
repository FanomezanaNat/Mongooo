package com.test.mongooo.config.database;

import com.mongodb.client.MongoClient;
import io.flamingock.internal.core.external.store.AuditStore;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlamingockAuditConfig {

  @Bean
  public MongoDBSyncTargetSystem mongoDBSyncTargetSystem(
      MongoClient mongoClient,
      @Value("${MONGO_DATABASE}") String databaseName) {
    return new MongoDBSyncTargetSystem("mongodb-target", mongoClient, databaseName);
  }

  @Bean
  public AuditStore auditStore(MongoDBSyncTargetSystem mongoDBSyncTargetSystem) {
    return MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem);
  }

}
