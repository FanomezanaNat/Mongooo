package com.test.mongooo.config.database.migration;

import com.test.mongooo.config.database.configuration.MongoIndexInitializer;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@TargetSystem(id = "mongodb-target")
@Change(id = "V001InitCollections", author = "fanomezana")
@Slf4j
public class _0001__CreateUsersIndex {

  private final MongoIndexInitializer indexInitializer;
  private final MongoDataInitializer dataInitializer;

  public _0001__CreateUsersIndex(MongoTemplate mongoTemplate) {
    this.indexInitializer = new MongoIndexInitializer(mongoTemplate);
    this.dataInitializer = new MongoDataInitializer(mongoTemplate);
  }


  @Apply
  public void execute() {
    indexInitializer.createAllIndexes();
    dataInitializer.seedAllData();
  }

  @Rollback
  public void rollback() {
    indexInitializer.dropAllIndexes();
  }
}

