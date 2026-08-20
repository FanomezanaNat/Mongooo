package com.bank.dbs.service;

import com.bank.dbs.constant.DocState;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.entity.DocUri;
import com.bank.dbs.exception.ArchivedDocumentException;
import com.bank.dbs.exception.DocNotFoundException;
import com.bank.dbs.repo.RepoInterface;
import com.bank.dbs.repo.RepoInterfaceFactory;
import com.bank.dbs.repository.DocRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Core orchestrator (spec 5.1 Services table: store, retrieve, delete,
 * storeStreaming, merge, updateMetadata). Controllers never touch DocRepository or
 * RepoInterface directly — everything funnels through here so validation, storage
 * routing, and version-chain rules are enforced in exactly one place.
 */
@Service
public class DocService {

    private static final Logger log = LoggerFactory.getLogger(DocService.class);
    private static final String PRIMARY_REPO_ID = "S3_PRIMARY";

    private final DocRepository docRepository;
    private final RepoInterfaceFactory repoInterfaceFactory;
    private final VersionService versionService;
    private final FileValidationService fileValidationService;

    public DocService(DocRepository docRepository,
                       RepoInterfaceFactory repoInterfaceFactory,
                       VersionService versionService,
                       FileValidationService fileValidationService) {
        this.docRepository = docRepository;
        this.repoInterfaceFactory = repoInterfaceFactory;
        this.versionService = versionService;
        this.fileValidationService = fileValidationService;
    }

    /**
     * Validates an already-assembled file (staged on NFS/EFS) and stores it under
     * the primary repo, creating either version 1 of a new chain or version N+1 of
     * an existing one depending on whether replaceRootDocId is supplied.
     */
    public Doc store(Path stagedFile, String filename, long fileSize, String fileFormat,
                      String docType, String docSubType, String customerId, UUID replaceRootDocId) {
        fileValidationService.validate(stagedFile, fileFormat, filename);

        RepoInterface primaryRepo = repoInterfaceFactory.resolve(PRIMARY_REPO_ID);
        String repoDocId;
        try (InputStream in = java.nio.file.Files.newInputStream(stagedFile)) {
            repoDocId = primaryRepo.store(UUID.randomUUID().toString(), in, fileSize, contentTypeFor(fileFormat));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to push validated file to primary storage", e);
        }

        DocUri docUri = new DocUri(PRIMARY_REPO_ID, repoDocId);

        Doc result;
        if (replaceRootDocId != null) {
            result = versionService.createNewVersion(replaceRootDocId, docUri, filename, fileSize, fileFormat);
        } else {
            result = versionService.createFirstVersion(
                    UUID.randomUUID(), docUri, filename, fileSize, fileFormat, docType, docSubType, customerId);
        }

        try {
            java.nio.file.Files.deleteIfExists(stagedFile);
        } catch (java.io.IOException e) {
            log.warn("Failed to clean up staged file {} after successful store", stagedFile, e);
        }

        return result;
    }

    /** Streams the current binary for docId to the given destination (>5MB streaming path). */
    public void retrieve(UUID docId, OutputStream destination) {
        Doc doc = getOrThrow(docId);
        RepoInterface repo = repoInterfaceFactory.resolve(
                doc.getArchiveDocUri() != null ? doc.getArchiveDocUri().getRepoId() : doc.getDocUri().getRepoId());
        DocUri uri = doc.getArchiveDocUri() != null ? doc.getArchiveDocUri() : doc.getDocUri();
        repo.get(uri.getRepoDocId(), destination);
    }

    public Doc getMetadata(UUID docId) {
        return getOrThrow(docId);
    }

    /** AC-BE-09: DELETE returns 403 ARCHIVED (not 404) for documents archived to CMOD. */
    public void delete(UUID docId) {
        Doc doc = getOrThrow(docId);
        if (doc.getArchiveDocUri() != null) {
            throw new ArchivedDocumentException(docId);
        }

        RepoInterface repo = repoInterfaceFactory.resolve(doc.getDocUri().getRepoId());
        repo.delete(doc.getDocUri().getRepoDocId());
        docRepository.delete(doc);
    }

    /**
     * updateMetadata(): sets docType/docSubType post-upload. @Retryable on
     * OptimisticLockingFailureException per US-057 / AC-BE-13 (3 retries with
     * 50ms/200ms/800ms backoff, 4th failure -> 409 CONFLICT via @Recover).
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 50, multiplier = 4, maxDelay = 800)
    )
    public Doc updateMetadata(UUID docId, String docType, String docSubType) {
        Doc doc = getOrThrow(docId);
        doc.setDocType(docType);
        doc.setDocSubType(docSubType);
        doc.setDtUpdated(Instant.now());
        return docRepository.save(doc); // @Version-guarded write
    }

    @Recover
    public Doc recoverUpdateMetadata(OptimisticLockingFailureException ex, UUID docId, String docType, String docSubType) {
        throw new com.bank.dbs.exception.ConcurrentModificationConflictException(docId.toString());
    }

    private Doc getOrThrow(UUID docId) {
        return docRepository.findById(docId).orElseThrow(() -> new DocNotFoundException(docId));
    }

    private String contentTypeFor(String fileFormat) {
        return switch (fileFormat.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "tiff" -> "image/tiff";
            default -> "application/octet-stream";
        };
    }
}
