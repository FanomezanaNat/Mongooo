package com.bank.dbs.exception;

/** 422 ENCRYPTED_DOCUMENT — PDF is password-encrypted; upload rejected (AC-BE-03). */
public class EncryptedDocumentException extends RuntimeException {
    public EncryptedDocumentException(String filename) {
        super("Document is password-encrypted and cannot be accepted: " + filename);
    }
}
