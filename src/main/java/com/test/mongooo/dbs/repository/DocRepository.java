package com.bank.dbs.repository;

import com.bank.dbs.constant.DocState;
import com.bank.dbs.entity.Doc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocRepository extends MongoRepository<Doc, UUID> {

    /** Uses idx_rootDocId_isCurrent — the hot path for "get current version" (AC-DB-02). */
    Optional<Doc> findByRootDocIdAndIsCurrentTrue(UUID rootDocId);

    /** Full version chain, ordered oldest-first, using idx_rootDocId_versionNumber. */
    List<Doc> findByRootDocIdOrderByVersionNumberAsc(UUID rootDocId);

    Optional<Doc> findByRootDocIdAndVersionNumber(UUID rootDocId, int versionNumber);

    /** Paginated dashboard listing, entitlement-filtered by caller at the service layer. */
    Page<Doc> findByCustomerIdAndDocTypeAndIsCurrentTrue(String customerId, String docType, Pageable pageable);

    /**
     * Merge-candidate / archival-eligibility lookup — uses
     * idx_customer_type_subtype_current.
     */
    List<Doc> findByCustomerIdAndDocTypeAndDocSubTypeAndIsCurrentTrue(
            String customerId, String docType, String docSubType);

    /**
     * PurgeScheduler source query: docs older than the retention cutoff, still in a
     * given lifecycle state. Uses idx_docState_dtCreated. AC-BE-08: purge must still
     * skip physical deletion for archived docs — that check happens in service code
     * against archiveDocUri, not in this query, since archiveDocUri is sparse-indexed
     * separately.
     */
    @Query("{ 'docState': ?0, 'dtCreated': { $lte: ?1 } }")
    List<Doc> findEligibleForPurge(DocState state, Instant purgeThreshold);

    /** Uses the sparse idx_archiveDocUri_sparse index — only docs with a value set. */
    @Query("{ 'archiveDocUri': { $ne: null } }")
    List<Doc> findArchivedDocs(Pageable pageable);

    /** Not-yet-archived + eligible docs are the ArchivalScheduler's batch source. */
    @Query("{ 'archiveDocUri': null, 'docState': 'ACTIVE' }")
    List<Doc> findNotYetArchived(Pageable pageable);
}
