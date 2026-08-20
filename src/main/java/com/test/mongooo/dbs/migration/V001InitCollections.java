package com.bank.dbs.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Creates all 13 indexes referenced in spec 6.3, seeds S3_PRIMARY / CMOD_ARCHIVE repo
 * configs, and seeds the 5 doc_archival_config entries from spec 6.5.
 *
 * Idempotency (AC-DB-01): createIndex() with the same name+spec is a no-op on
 * re-run; seed inserts use replaceOne(..., upsert=true) keyed on natural _id so
 * re-running never duplicates seed rows.
 */
@ChangeUnit(id = "V001InitCollections", order = "001", author = "dbs-team")
public class V001InitCollections {

    @Execution
    public void execution(MongoDatabase db) {
        createDocsIndexes(db);
        createTasksIndex(db);
        createSignedUrlsIndexes(db);
        createApplicationDocsIndexes(db);
        createDocLocksIndex(db);
        seedRepoConfigs(db);
        seedDocArchivalConfig(db);
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        // Index/seed-data rollback is intentionally a no-op: dropping indexes on a
        // live collection is destructive and V001 is expected to be forward-only.
        // A dedicated down-migration would be added here if genuinely required.
    }

    private void createDocsIndexes(MongoDatabase db) {
        MongoCollection<Document> docs = db.getCollection("docs");
        docs.createIndex(Indexes.ascending("rootDocId", "isCurrent"),
                new IndexOptions().name("idx_rootDocId_isCurrent"));
        docs.createIndex(Indexes.ascending("rootDocId", "versionNumber"),
                new IndexOptions().name("idx_rootDocId_versionNumber"));
        docs.createIndex(Indexes.ascending("customerId", "docType", "docSubType", "isCurrent"),
                new IndexOptions().name("idx_customer_type_subtype_current"));
        docs.createIndex(Indexes.ascending("docState", "dtCreated"),
                new IndexOptions().name("idx_docState_dtCreated"));
        docs.createIndex(Indexes.ascending("archiveDocUri"),
                new IndexOptions().name("idx_archiveDocUri_sparse").sparse(true));
    }

    private void createTasksIndex(MongoDatabase db) {
        MongoCollection<Document> tasks = db.getCollection("tasks");
        // Partial index — only unprocessed tasks are indexed (AC-DB-05: >50% smaller).
        tasks.createIndex(Indexes.ascending("taskType", "processed", "dtCreated"),
                new IndexOptions()
                        .name("idx_taskType_processed_dtCreated_partial")
                        .partialFilterExpression(new Document("processed", false)));
    }

    private void createSignedUrlsIndexes(MongoDatabase db) {
        MongoCollection<Document> signedUrls = db.getCollection("signed_urls");
        signedUrls.createIndex(Indexes.ascending("tokenHash"),
                new IndexOptions().name("idx_tokenHash_unique").unique(true));
        signedUrls.createIndex(Indexes.ascending("status", "expiresAt"),
                new IndexOptions().name("idx_status_expiresAt"));
        // TTL: auto-delete 24h (86400s) after ttlAnchor (set to expiresAt at write time).
        signedUrls.createIndex(Indexes.ascending("ttlAnchor"),
                new IndexOptions().name("idx_expiresAt_ttl").expireAfter(86400L, TimeUnit.SECONDS));
    }

    private void createApplicationDocsIndexes(MongoDatabase db) {
        MongoCollection<Document> appDocs = db.getCollection("application_docs");
        appDocs.createIndex(Indexes.ascending("applicationId", "docType", "docSubType", "status"),
                new IndexOptions().name("idx_appId_type_subtype_status"));
        appDocs.createIndex(Indexes.ascending("rootDocId"),
                new IndexOptions().name("idx_rootDocId"));
    }

    private void createDocLocksIndex(MongoDatabase db) {
        MongoCollection<Document> docLocks = db.getCollection("doc_locks");
        // expireAfter(0) => document is removed the instant expiresAt is reached.
        docLocks.createIndex(Indexes.ascending("expiresAt"),
                new IndexOptions().name("idx_expiresAt_ttl_immediate").expireAfter(0L, TimeUnit.SECONDS));
    }

    private void seedRepoConfigs(MongoDatabase db) {
        MongoCollection<Document> repoConfigs = db.getCollection("repo_configs");

        Document s3Primary = new Document("_id", "S3_PRIMARY")
                .append("repoType", "S3")
                .append("beanName", "s3RepoImpl")
                .append("container", "dbs-documents")
                .append("active", true);

        Document cmodArchive = new Document("_id", "CMOD_ARCHIVE")
                .append("repoType", "CMOD")
                .append("beanName", "cmodRepoImpl")
                .append("container", "DBS_ARCHIVE_GROUP")
                .append("active", true);

        upsertById(repoConfigs, s3Primary);
        upsertById(repoConfigs, cmodArchive);
    }

    private void seedDocArchivalConfig(MongoDatabase db) {
        MongoCollection<Document> archivalConfig = db.getCollection("doc_archival_config");

        List<String[]> entries = List.of(
                new String[]{"KYC", "PASSPORT"},
                new String[]{"KYC", "UTILITY_BILL"},
                new String[]{"TRADE_FINANCE", "LC"},
                new String[]{"TRADE_FINANCE", "INVOICE"},
                new String[]{"ONBOARDING", "APPLICATION_FORM"}
        );

        for (String[] entry : entries) {
            String docType = entry[0];
            String docSubType = entry[1];
            Document doc = new Document("_id", docType + ":" + docSubType)
                    .append("docType", docType)
                    .append("docSubType", docSubType)
                    .append("archivalEnabled", true)
                    .append("targetRepoId", "CMOD_ARCHIVE");
            upsertById(archivalConfig, doc);
        }
    }

    private void upsertById(MongoCollection<Document> collection, Document doc) {
        collection.replaceOne(
                new Document("_id", doc.get("_id")),
                doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }
}
