package com.bank.dbs.service;

import com.bank.dbs.constant.DocState;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.entity.DocUri;
import com.bank.dbs.exception.ConcurrentVersionCreationException;
import com.bank.dbs.repository.DocRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * @Transactional createNewVersion(): atomic flip isCurrent=false + insert new doc
 * (spec 5.1 Services table). Backed by MongoDB multi-document transactions
 * (MongoTransactionManager bound to the primary MongoDatabaseFactory — see
 * MongoConfig), which is the entire reason MongoDB replica-set transactions were
 * chosen over a single-document workaround (spec 2.1 rationale).
 *
 * Invariant enforced (AC-BE-11): at no point are zero or two Doc records with
 * isCurrent=true for the same rootDocId.
 */
@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    private final DocRepository docRepository;
    private final DistributedLockService distributedLockService;

    public VersionService(DocRepository docRepository, DistributedLockService distributedLockService) {
        this.docRepository = docRepository;
        this.distributedLockService = distributedLockService;
    }

    /**
     * Creates version N+1 for the given rootDocId chain: the current version is
     * flipped to isCurrent=false / REPLACED, and a new Doc is inserted as the new
     * current version — both writes happen inside a single MongoDB transaction so a
     * reader can never observe zero or two "current" versions.
     *
     * A short-lived distributed lock on rootDocId additionally guards against two
     * concurrent replacement requests racing each other into the same transaction
     * window (AC-BE — "another pod is creating a version for same rootDocId" ->
     * 409 VERSION_CONFLICT, per ConcurrentVersionCreationException / spec 5.2).
     */
    @Transactional("transactionManager")
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 50, multiplier = 4, maxDelay = 800) // 50ms/200ms/800ms
    )
    public Doc createNewVersion(UUID rootDocId, DocUri newDocUri, String filename, long fileSize, String fileFormat) {
        boolean locked = distributedLockService.tryAcquire(rootDocId, Duration.ofSeconds(30));
        if (!locked) {
            throw new ConcurrentVersionCreationException(rootDocId);
        }

        try {
            Doc currentVersion = docRepository.findByRootDocIdAndIsCurrentTrue(rootDocId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No current version found for rootDocId=" + rootDocId + "; cannot create new version"));

            // Flip old version to REPLACED / isCurrent=false.
            currentVersion.setCurrent(false);
            currentVersion.setDocState(DocState.REPLACED);
            currentVersion.setDtUpdated(Instant.now());
            docRepository.save(currentVersion); // relies on @Version for the retryable optimistic lock

            // Insert the new current version.
            Doc newVersion = new Doc();
            newVersion.setId(UUID.randomUUID());
            newVersion.setRootDocId(rootDocId);
            newVersion.setVersionNumber(currentVersion.getVersionNumber() + 1);
            newVersion.setCurrent(true);
            newVersion.setPreviousVersionId(currentVersion.getId());
            newVersion.setDocState(DocState.ACTIVE);
            newVersion.setDocUri(newDocUri);
            newVersion.setFilename(filename);
            newVersion.setFileSize(fileSize);
            newVersion.setFileFormat(fileFormat);
            newVersion.setDocType(currentVersion.getDocType());
            newVersion.setDocSubType(currentVersion.getDocSubType());
            newVersion.setCustomerId(currentVersion.getCustomerId());
            newVersion.setDtCreated(Instant.now());
            newVersion.setDtUpdated(Instant.now());

            Doc saved = docRepository.save(newVersion);
            log.info("Created version {} for rootDocId={} (docId={})",
                    saved.getVersionNumber(), rootDocId, saved.getId());
            return saved;
        } finally {
            distributedLockService.release(rootDocId);
        }
    }

    /** Creates the first version (version 1) of a brand-new document chain. */
    public Doc createFirstVersion(UUID docId, DocUri docUri, String filename, long fileSize, String fileFormat,
                                   String docType, String docSubType, String customerId) {
        Doc doc = new Doc();
        doc.setId(docId);
        doc.setRootDocId(docId); // rootDocId == _id for version 1, per spec glossary
        doc.setVersionNumber(1);
        doc.setCurrent(true);
        doc.setDocState(DocState.ACTIVE);
        doc.setDocUri(docUri);
        doc.setFilename(filename);
        doc.setFileSize(fileSize);
        doc.setFileFormat(fileFormat);
        doc.setDocType(docType);
        doc.setDocSubType(docSubType);
        doc.setCustomerId(customerId);
        doc.setDtCreated(Instant.now());
        doc.setDtUpdated(Instant.now());
        return docRepository.save(doc);
    }

    @Recover
    public Doc recoverFromOptimisticLockFailure(OptimisticLockingFailureException ex, UUID rootDocId,
                                                 DocUri newDocUri, String filename, long fileSize, String fileFormat) {
        // AC-BE-13: 4th consecutive failure surfaces as 409 CONFLICT rather than a
        // 500, since it's a legitimate concurrency outcome the client can retry.
        throw new com.bank.dbs.exception.ConcurrentModificationConflictException(rootDocId.toString());
    }
}
