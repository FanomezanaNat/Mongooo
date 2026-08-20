package com.bank.dbs.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

/**
 * Distributed lock used by ArchivalScheduler to guarantee a document is only
 * archived by a single pod at a time (AC-BE-12).
 *
 * DistributedLockService.tryAcquire() performs mongoTemplate.insert(DocLock) and
 * relies on the unique _id (the docId being locked) throwing DuplicateKeyException
 * when another pod already holds the lock — this makes acquisition atomic without
 * needing findAndModify or external Redis infrastructure.
 *
 * TTL index with expireAfterSeconds=0 means MongoDB deletes the document as soon as
 * the clock passes `expiresAt`, auto-releasing locks held by crashed pods.
 */
@Document(collection = "doc_locks")
public class DocLock {

    /** The docId being locked is used directly as _id to make acquisition atomic. */
    @Id
    private UUID id;

    @Field("ownerId")
    private String ownerId; // pod identity / instance id, used to guard release()

    @Field("acquiredAt")
    private Instant acquiredAt;

    @Indexed(name = "idx_expiresAt_ttl_immediate", expireAfterSeconds = 0)
    @Field("expiresAt")
    private Instant expiresAt;

    public DocLock() {
    }

    public DocLock(UUID id, String ownerId, Instant acquiredAt, Instant expiresAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(Instant acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
