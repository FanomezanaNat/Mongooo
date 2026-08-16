package com.test.mongooo.config.database.migration;

import com.test.mongooo.config.database.schema.MongoSchema;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.bson.Document;


@Slf4j
public class MongoDataInitializer {
  private final MongoTemplate mongoTemplate;

  public MongoDataInitializer(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Insère ou met à jour l'ensemble des données initiales.
   */
  public void seedAllData() {
    insertRepositories();
    insertArchivalConfigs();
  }

  /**
   * Inserte/Mise à jour idempotente des Repositories (S3_PRIMARY et CMOD_ARCHIVE).
   */
  public void insertRepositories() {
    List<Document> repos = List.of(
        new Document(Map.of(
            MongoSchema.Fields.ID, "S3_PRIMARY",
            MongoSchema.Fields.TYPE, "S3",
            MongoSchema.Fields.ENABLED, true,
            MongoSchema.Fields.DT_CREATED, new Date()
        )),
        new Document(Map.of(
            MongoSchema.Fields.ID, "CMOD_ARCHIVE",
            MongoSchema.Fields.TYPE, "CMOD",
            MongoSchema.Fields.ENABLED, true,
            MongoSchema.Fields.DT_CREATED, new Date()
        ))
    );

    upsertDocuments(MongoSchema.Collections.REPOSITORIES, repos);
  }

  /**
   * Inserte/Mise à jour idempotente des 5 configurations d'archivage.
   */
  public void insertArchivalConfigs() {
    List<Document> configs = List.of(
        createArchivalConfig("KYC", "PASSPORT"),
        createArchivalConfig("KYC", "UTILITY_BILL"),
        createArchivalConfig("TRADE_FINANCE", "LC"),
        createArchivalConfig("TRADE_FINANCE", "INVOICE"),
        createArchivalConfig("ONBOARDING", "APPLICATION_FORM")
    );

    upsertDocuments(MongoSchema.Collections.DOC_ARCHIVAL_CONFIG, configs);
  }

  private Document createArchivalConfig(String docType, String docSubType) {
    return new Document(Map.of(
        MongoSchema.Fields.ID, docType + "_" + docSubType,
        MongoSchema.Fields.DOC_TYPE, docType,
        MongoSchema.Fields.DOC_SUB_TYPE, docSubType,
        MongoSchema.Fields.ENABLED, true,
        MongoSchema.Fields.DT_CREATED, new Date()
    ));
  }

  /**
   * Méthode générique d'Upsert pour garantir l'idempotence des documents insérés.
   */
  private void upsertDocuments(String collectionName, List<Document> documents) {
    for (Document doc : documents) {
      try {
        Object id = doc.get(MongoSchema.Fields.ID);
        Query query = new Query(Criteria.where(MongoSchema.Fields.ID).is(id));
        Update update = Update.fromDocument(doc);
        mongoTemplate.upsert(query, update, collectionName);
        log.info("Document avec _id '{}' mis à jour/inséré dans '{}'.", id, collectionName);
      } catch (Exception e) {
        log.error("Erreur lors de l'upsert dans '{}' pour _id '{}' : {}",
            collectionName, doc.get(MongoSchema.Fields.ID), e.getMessage());
      }
    }
  }
}
