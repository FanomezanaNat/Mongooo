package com.test.mongooo.config.database.migration;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@TargetSystem(id = "mongodb-target")
@Change(id = "create-user-email-index", author = "fanomezana")
@Slf4j
public class _0001__CreateUsersIndex {

  private static final String COLLECTION_NAME = "users";
  private static final String INDEX_NAME = "uk_users_email";

  private final MongoTemplate mongoTemplate;

  public _0001__CreateUsersIndex(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }


  @Apply
  public void execute() {
    IndexOperations indexOps = mongoTemplate.indexOps(COLLECTION_NAME);

    try {
      indexOps.createIndex(
          new Index()
              .on("email", Sort.Direction.ASC)
              .unique()
              .named(INDEX_NAME)
      );
      log.info("Index '{}' appliqué avec succès sur '{}'.", INDEX_NAME, COLLECTION_NAME);
    } catch (DataAccessException e) {
      // Si MongoDB renvoie une erreur parce qu'un index équivalent ou en conflit existe déjà, on ignore et on continue
      log.warn(
          "L'index sur 'email' existe déjà ou est en conflit d'options. Migration ignorée : {}",
          e.getMessage());
    }
  }

  @Rollback
  public void rollback() {
    IndexOperations indexOps = mongoTemplate.indexOps(COLLECTION_NAME);

    try {
      indexOps.dropIndex(INDEX_NAME);
      log.info("Index '{}' supprimé lors du rollback.", INDEX_NAME);
    } catch (DataAccessException e) {
      log.warn("Impossible de supprimer l'index '{}' lors du rollback (déjà inexistant) : {}",
          INDEX_NAME, e.getMessage());
    }
  }
}

