package com.bank.dbs.exception;

/** 410 URL_EXPIRED — signed URL TTL has passed (AC-BE-10). */
public class SignedUrlExpiredException extends RuntimeException {
    public SignedUrlExpiredException() {
        super("Signed URL has expired");
    }
}
