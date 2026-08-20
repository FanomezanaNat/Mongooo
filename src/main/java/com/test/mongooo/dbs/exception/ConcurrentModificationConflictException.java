package com.bank.dbs.exception;

/** 409 CONFLICT — optimistic lock retry exhausted after 3 attempts (AC-BE-13). */
public class ConcurrentModificationConflictException extends RuntimeException {
    public ConcurrentModificationConflictException(String docId) {
        super("Concurrent modification conflict on document after retry exhaustion: " + docId);
    }
}
