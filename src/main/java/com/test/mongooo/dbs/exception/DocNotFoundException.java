package com.bank.dbs.exception;

import java.util.UUID;

/** 404 DOC_NOT_FOUND */
public class DocNotFoundException extends RuntimeException {
    public DocNotFoundException(UUID docId) {
        super("Document not found: " + docId);
    }
}
