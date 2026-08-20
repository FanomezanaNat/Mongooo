package com.bank.dbs.scheduler;

import com.bank.dbs.constant.DocState;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.repo.RepoInterface;
import com.bank.dbs.repo.RepoInterfaceFactory;
import com.bank.dbs.repository.DocRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * cron 03:30 daily; 90-day retention; skips physical delete if archived
 * (spec 1.1 / AC-BE-08).
 *
 * IMPORTANT: AC-BE-08 requires we never physically delete a doc whose
 * archiveDocUri is non-null — the CMOD copy is the regulatory record of truth, and
 * deleting the primary-storage copy of an *unarchived* doc past its retention
 * window is what this scheduler actually does. Archived docs are left entirely
 * alone here (no metadata record deletion either), since their `docs` row is what
 * links back to the CMOD pointer for future retrieval.
 */
@Component
public class PurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgeScheduler.class);

    private final DocRepository docRepository;
    private final RepoInterfaceFactory repoInterfaceFactory;
    private final int retentionDays;

    public PurgeScheduler(DocRepository docRepository,
                           RepoInterfaceFactory repoInterfaceFactory,
                           @Value("${dbs.purge.retention-days:90}") int retentionDays) {
        this.docRepository = docRepository;
        this.repoInterfaceFactory = repoInterfaceFactory;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 3 * * *") // 03:30 daily
    public void run() {
        Instant threshold = Instant.now().minus(Duration.ofDays(retentionDays));

        // REPLACED docs older than retention that were never archived are the
        // primary purge target — ACTIVE current-version docs are not purge
        // candidates regardless of age (only superseded versions age out).
        List<Doc> candidates = docRepository.findEligibleForPurge(DocState.REPLACED, threshold);
        log.info("PurgeScheduler: {} REPLACED docs older than {} days found", candidates.size(), retentionDays);

        int purged = 0;
        int skippedArchived = 0;

        for (Doc doc : candidates) {
            if (doc.getArchiveDocUri() != null) {
                // AC-BE-08: archived docs are never physically deleted from primary
                // storage by this scheduler — the archive copy is the retained record.
                skippedArchived++;
                continue;
            }

            try {
                RepoInterface repo = repoInterfaceFactory.resolve(doc.getDocUri().getRepoId());
                repo.delete(doc.getDocUri().getRepoDocId());
                docRepository.delete(doc);
                purged++;
            } catch (Exception e) {
                log.error("Failed to purge docId={}", doc.getId(), e);
            }
        }

        log.info("PurgeScheduler completed: purged={}, skippedArchived={}", purged, skippedArchived);
    }
}
