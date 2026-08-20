package com.bank.dbs.exception;

/** 401 INVALID_TOKEN — HMAC signature invalid or tampered. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String reason) {
        super("Invalid signed URL token: " + reason);
    }
}
