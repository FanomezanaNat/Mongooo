package com.bank.dbs.exception;

import java.util.UUID;

/** 403 DOC_ARCHIVED — cannot delete or replace an archived document (AC-BE-09). */
public class ArchivedDocumentException extends RuntimeException {
    public ArchivedDocumentException(UUID docId) {
        super("Document is archived and cannot be modified or deleted: " + docId);
    }
}
