package com.bank.dbs.entity;

import com.bank.dbs.constant.SignedUrlStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the lifecycle of an HMAC-signed upload/download URL (spec 6.2 "signed_urls").
 *
 * expiresAt carries a TTL index (expireAfterSeconds = 86400) so MongoDB purges the
 * record 24h *after* the token's own expiry — AC-DB-03. tokenHash (not the raw token)
 * is stored and indexed for O(1) validation lookups without persisting the secret itself.
 */
@Document(collection = "signed_urls")
@CompoundIndexes({
        // Link-expiry cleanup job queries
        @CompoundIndex(name = "idx_status_expiresAt", def = "{'status': 1, 'expiresAt': 1}")
})
public class SignedUrl {

    @Id
    private UUID id;

    /** SHA-256 hash of the raw HMAC token — never store the raw token. */
    @Indexed(name = "idx_tokenHash_unique", unique = true)
    @Field("tokenHash")
    private String tokenHash;

    @Field("docId")
    private UUID docId;

    @Field("direction")
    private String direction; // UPLOAD | DOWNLOAD

    @Field("chunkSizeMb")
    private Integer chunkSizeMb;

    @Field("totalChunks")
    private Integer totalChunks;

    @Field("receivedChunks")
    private int receivedChunks;

    @Field("status")
    private SignedUrlStatus status;

    @Field("keyId")
    private String keyId; // supports HMAC key rotation window (R05)

    @CreatedDate
    @Field("dtCreated")
    private Instant dtCreated;

    @Field("expiresAt")
    private Instant expiresAt;

    /**
     * TTL index: auto-deletes 24h after expiresAt has passed. MongoDB computes this as
     * expiresAt + expireAfterSeconds, so expireAfterSeconds is set to 86400 (24h)
     * on top of the expiresAt date field itself.
     */
    @Indexed(name = "idx_expiresAt_ttl", expireAfterSeconds = 86400)
    @Field("ttlAnchor")
    private Instant ttlAnchor;

    public SignedUrl() {
    }

    // --- Getters / Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getDocId() {
        return docId;
    }

    public void setDocId(UUID docId) {
        this.docId = docId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Integer getChunkSizeMb() {
        return chunkSizeMb;
    }

    public void setChunkSizeMb(Integer chunkSizeMb) {
        this.chunkSizeMb = chunkSizeMb;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getReceivedChunks() {
        return receivedChunks;
    }

    public void setReceivedChunks(int receivedChunks) {
        this.receivedChunks = receivedChunks;
    }

    public SignedUrlStatus getStatus() {
        return status;
    }

    public void setStatus(SignedUrlStatus status) {
        this.status = status;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Instant getDtCreated() {
        return dtCreated;
    }

    public void setDtCreated(Instant dtCreated) {
        this.dtCreated = dtCreated;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getTtlAnchor() {
        return ttlAnchor;
    }

    public void setTtlAnchor(Instant ttlAnchor) {
        this.ttlAnchor = ttlAnchor;
    }
}
