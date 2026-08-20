package com.bank.dbs.exception;

import java.util.UUID;

/** 409 VERSION_CONFLICT — another pod is creating a version for the same rootDocId. */
public class ConcurrentVersionCreationException extends RuntimeException {
    public ConcurrentVersionCreationException(UUID rootDocId) {
        super("Another process is already creating a new version for rootDocId: " + rootDocId);
    }
}
