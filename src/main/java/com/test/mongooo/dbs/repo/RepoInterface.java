package com.bank.dbs.repo;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Storage contract every backend (S3, CMOD, FS) implements. See C4 Level 2 diagram
 * (section 2) — DBS never lets a controller or scheduler talk to a storage SDK
 * directly; everything routes through this interface so the primary/archive/staging
 * backends can be swapped without touching business logic.
 */
public interface RepoInterface {

    /**
     * Persists the given stream under the given docId, returning the backend-specific
     * pointer (S3 key, CMOD OD document id, or FS relative path) to be stored in
     * Doc.docUri / Doc.archiveDocUri.
     */
    String store(String docId, InputStream content, long contentLength, String contentType);

    /** Streams the file identified by repoDocId into the given OutputStream. */
    void get(String repoDocId, OutputStream destination);

    /** Returns a fresh InputStream for the file identified by repoDocId. Caller closes it. */
    InputStream getAsStream(String repoDocId);

    void delete(String repoDocId);

    /**
     * Copies a document already resident in this repo out to CMOD (or another archive
     * target) — used by ArchivalScheduler. Only meaningful on the archive repo
     * implementation; primary repos (S3/FS) do not implement true archival semantics
     * beyond acting as the read source for the copy.
     */
    default String archive(String repoDocId, InputStream content, long contentLength, String contentType) {
        return store(repoDocId, content, contentLength, contentType);
    }

    /** repoId this implementation is registered under in repo_configs (e.g. "S3_PRIMARY"). */
    String repoId();
}
