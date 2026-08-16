package com.test.mongooo.config.database.configuration;

import com.test.mongooo.config.database.schema.MongoSchema;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class MongoIndexInitializer {
  private final MongoTemplate mongoTemplate;

  public MongoIndexInitializer(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public void createAllIndexes() {
    createDocsIndexes();
    createTasksIndexes();
    createSignedUrlsIndexes();
    createApplicationDocsIndexes();
    createDocLocksIndexes();
  }

  public void dropAllIndexes() {
    dropIndexIfExists(MongoSchema.Collections.DOCS, "idx_docs_root_current");
    dropIndexIfExists(MongoSchema.Collections.DOCS, "idx_docs_root_version");
    dropIndexIfExists(MongoSchema.Collections.DOCS, "idx_docs_customer_type_subtype_current");
    dropIndexIfExists(MongoSchema.Collections.DOCS, "idx_docs_state_dtcreated");
    dropIndexIfExists(MongoSchema.Collections.DOCS, "idx_docs_archive_uri_sparse");

    dropIndexIfExists(MongoSchema.Collections.TASKS, "idx_tasks_type_unprocessed_created");

    dropIndexIfExists(MongoSchema.Collections.SIGNED_URLS, "uk_signed_urls_token_hash");
    dropIndexIfExists(MongoSchema.Collections.SIGNED_URLS, "idx_signed_urls_status_expires");
    dropIndexIfExists(MongoSchema.Collections.SIGNED_URLS, "ttl_signed_urls_expires");

    dropIndexIfExists(MongoSchema.Collections.APPLICATION_DOCS, "idx_app_docs_app_type_subtype_status");
    dropIndexIfExists(MongoSchema.Collections.APPLICATION_DOCS, "idx_app_docs_root_doc_id");

    dropIndexIfExists(MongoSchema.Collections.DOC_LOCKS, "ttl_doc_locks_expires");
  }

  // --- Configurations des Index ---

  private void createDocsIndexes() {
    IndexOperations ops = mongoTemplate.indexOps(MongoSchema.Collections.DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.ROOT_DOC_ID, Sort.Direction.ASC)
        .on(MongoSchema.Fields.IS_CURRENT, Sort.Direction.ASC)
        .named("idx_docs_root_current"), MongoSchema.Collections.DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.ROOT_DOC_ID, Sort.Direction.ASC)
        .on(MongoSchema.Fields.VERSION_NUMBER, Sort.Direction.ASC)
        .named("idx_docs_root_version"), MongoSchema.Collections.DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.CUSTOMER_ID, Sort.Direction.ASC)
        .on(MongoSchema.Fields.DOC_TYPE, Sort.Direction.ASC)
        .on(MongoSchema.Fields.DOC_SUB_TYPE, Sort.Direction.ASC)
        .on(MongoSchema.Fields.IS_CURRENT, Sort.Direction.ASC)
        .named("idx_docs_customer_type_subtype_current"), MongoSchema.Collections.DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.DOC_STATE, Sort.Direction.ASC)
        .on(MongoSchema.Fields.DT_CREATED, Sort.Direction.ASC)
        .named("idx_docs_state_dtcreated"), MongoSchema.Collections.DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.ARCHIVE_DOC_URI, Sort.Direction.ASC)
        .sparse()
        .named("idx_docs_archive_uri_sparse"), MongoSchema.Collections.DOCS);
  }

  private void createTasksIndexes() {
    IndexOperations ops = mongoTemplate.indexOps(MongoSchema.Collections.TASKS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.TASK_TYPE, Sort.Direction.ASC)
        .on(MongoSchema.Fields.PROCESSED, Sort.Direction.ASC)
        .on(MongoSchema.Fields.DT_CREATED, Sort.Direction.ASC)
        .partial(PartialIndexFilter.of(Criteria.where(MongoSchema.Fields.PROCESSED).is(false)))
        .named("idx_tasks_type_unprocessed_created"), MongoSchema.Collections.TASKS);
  }

  private void createSignedUrlsIndexes() {
    IndexOperations ops = mongoTemplate.indexOps(MongoSchema.Collections.SIGNED_URLS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.TOKEN_HASH, Sort.Direction.ASC)
        .unique()
        .named("uk_signed_urls_token_hash"), MongoSchema.Collections.SIGNED_URLS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.STATUS, Sort.Direction.ASC)
        .on(MongoSchema.Fields.EXPIRES_AT, Sort.Direction.ASC)
        .named("idx_signed_urls_status_expires"), MongoSchema.Collections.SIGNED_URLS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.EXPIRES_AT, Sort.Direction.ASC)
        .expire(Duration.ofSeconds(86400))
        .named("ttl_signed_urls_expires"), MongoSchema.Collections.SIGNED_URLS);
  }

  private void createApplicationDocsIndexes() {
    IndexOperations ops = mongoTemplate.indexOps(MongoSchema.Collections.APPLICATION_DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.APPLICATION_ID, Sort.Direction.ASC)
        .on(MongoSchema.Fields.DOC_TYPE, Sort.Direction.ASC)
        .on(MongoSchema.Fields.DOC_SUB_TYPE, Sort.Direction.ASC)
        .on(MongoSchema.Fields.STATUS, Sort.Direction.ASC)
        .named("idx_app_docs_app_type_subtype_status"), MongoSchema.Collections.APPLICATION_DOCS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.ROOT_DOC_ID, Sort.Direction.ASC)
        .named("idx_app_docs_root_doc_id"), MongoSchema.Collections.APPLICATION_DOCS);
  }

  private void createDocLocksIndexes() {
    IndexOperations ops = mongoTemplate.indexOps(MongoSchema.Collections.DOC_LOCKS);

    createIndexIfMissing(ops, new Index()
        .on(MongoSchema.Fields.EXPIRES_AT, Sort.Direction.ASC)
        .expire(Duration.ofSeconds(0))
        .named("ttl_doc_locks_expires"), MongoSchema.Collections.DOC_LOCKS);
  }

  // --- Logique d'Idempotence Sans Suppression ---

  /**
   * Vérifie si l'index existe déjà. S'il n'existe pas, il est créé.
   * En cas d'erreur réseau/concurrence, le bloc try-catch prévient tout plantage.
   */
  private void createIndexIfMissing(IndexOperations ops, Index index, String collectionName) {
    String indexName = (String) index.getIndexOptions().get("name");

    if (indexExists(ops, indexName)) {
      log.info("Index '{}' existe déjà sur '{}'. Migration ignorée.", indexName, collectionName);
      return;
    }

    try {
      ops.createIndex(index);
      log.info("Index '{}' créé avec succès sur '{}'.", indexName, collectionName);
    } catch (DataAccessException e) {
      log.warn("Impossible de créer l'index '{}' sur '{}' (peut-être créé en parallèle) : {}",
          indexName, collectionName, e.getMessage());
    }
  }

  private boolean indexExists(IndexOperations ops, String indexName) {
    try {
      List<IndexInfo> existingIndexes = ops.getIndexInfo();
      return existingIndexes.stream().anyMatch(info -> indexName.equals(info.getName()));
    } catch (DataAccessException e) {
      // Si la collection n'existe pas encore
      return false;
    }
  }

  private void dropIndexIfExists(String collectionName, String indexName) {
    IndexOperations ops = mongoTemplate.indexOps(collectionName);
    if (indexExists(ops, indexName)) {
      try {
        ops.dropIndex(indexName);
        log.info("Index '{}' supprimé sur '{}'.", indexName, collectionName);
      } catch (DataAccessException e) {
        log.warn("Impossible de supprimer l'index '{}' sur '{}' : {}", indexName, collectionName, e.getMessage());
      }
    }
  }
}
