package com.bank.dbs.entity;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Objects;

/**
 * Embedded pointer to a physical file location within a given repo implementation
 * (S3 key, CMOD OD document id, or FS path) — spec section 6.2.
 *
 * Used for both {@code docUri} (primary storage, always non-null once ACTIVE) and
 * {@code archiveDocUri} (CMOD pointer, null until the ArchivalScheduler completes).
 */
public class DocUri {

    @Field("repoId")
    private String repoId;

    @Field("repoDocId")
    private String repoDocId;

    public DocUri() {
    }

    public DocUri(String repoId, String repoDocId) {
        this.repoId = repoId;
        this.repoDocId = repoDocId;
    }

    public String getRepoId() {
        return repoId;
    }

    public void setRepoId(String repoId) {
        this.repoId = repoId;
    }

    public String getRepoDocId() {
        return repoDocId;
    }

    public void setRepoDocId(String repoDocId) {
        this.repoDocId = repoDocId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocUri)) return false;
        DocUri docUri = (DocUri) o;
        return Objects.equals(repoId, docUri.repoId) && Objects.equals(repoDocId, docUri.repoDocId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoId, repoDocId);
    }

    @Override
    public String toString() {
        return "DocUri{repoId='" + repoId + "', repoDocId='" + repoDocId + "'}";
    }
}
