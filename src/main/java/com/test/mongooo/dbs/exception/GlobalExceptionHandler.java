package com.bank.dbs.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;

/**
 * Central exception -> HTTP status mapping (spec 5.2). Every handler returns a
 * consistent { errorCode, message, timestamp } body so the Angular NotificationComponent
 * can render a human-readable toast (AC-FE-11).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(DocNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "DOC_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ArchivedDocumentException.class)
    public ResponseEntity<Object> handleArchived(ArchivedDocumentException ex) {
        return body(HttpStatus.FORBIDDEN, "DOC_ARCHIVED", ex.getMessage());
    }

    @ExceptionHandler(EncryptedDocumentException.class)
    public ResponseEntity<Object> handleEncrypted(EncryptedDocumentException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "ENCRYPTED_DOCUMENT", ex.getMessage());
    }

    @ExceptionHandler(VirusDetectedException.class)
    public ResponseEntity<Object> handleVirus(VirusDetectedException ex) {
        // Deliberately do not echo the raw ClamAV signature name back to the client
        // beyond what the exception message already contains — avoid information
        // leakage about internal AV rule names in a bank-facing API.
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "VIRUS_DETECTED", "Uploaded file failed antivirus scan");
    }

    @ExceptionHandler(FormatMismatchException.class)
    public ResponseEntity<Object> handleFormatMismatch(FormatMismatchException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "FORMAT_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidToken(InvalidTokenException ex) {
        return body(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(SignedUrlExpiredException.class)
    public ResponseEntity<Object> handleExpiredUrl(SignedUrlExpiredException ex) {
        return body(HttpStatus.GONE, "URL_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(ConcurrentModificationConflictException.class)
    public ResponseEntity<Object> handleConflict(ConcurrentModificationConflictException ex) {
        return body(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(ConcurrentVersionCreationException.class)
    public ResponseEntity<Object> handleVersionConflict(ConcurrentVersionCreationException ex) {
        return body(HttpStatus.CONFLICT, "VERSION_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(RepoInstantiationException.class)
    public ResponseEntity<Object> handleRepoError(RepoInstantiationException ex) {
        log.error("Repo instantiation failure", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "REPO_ERROR", "Storage backend unavailable");
    }

    @ExceptionHandler(com.bank.dbs.exception.MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleTooLargeCustom(com.bank.dbs.exception.MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", ex.getMessage());
    }

    /** Spring's own multipart-size guard (spring.servlet.multipart.max-file-size). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleTooLargeSpring(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file exceeds the 25 MB limit");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequest(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<Object> body(HttpStatus status, String errorCode, String message) {
        Map<String, Object> payload = Map.of(
                "errorCode", errorCode,
                "message", message,
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(status).body(payload);
    }
}
