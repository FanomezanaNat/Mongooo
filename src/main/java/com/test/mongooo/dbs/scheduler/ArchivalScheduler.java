package com.bank.dbs.scheduler;

import com.bank.dbs.constant.TaskType;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.entity.DocUri;
import com.bank.dbs.entity.Task;
import com.bank.dbs.repo.RepoInterface;
import com.bank.dbs.repo.RepoInterfaceFactory;
import com.bank.dbs.repository.DocRepository;
import com.bank.dbs.repository.TaskRepository;
import com.bank.dbs.service.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * cron 02:15 daily; batch 50 DOC_ARCHIVAL tasks; distributed lock per doc
 * (spec 1.1 / 5.4 AC-BE-07, AC-BE-12).
 *
 * Each task has its own @Transactional boundary (AC-BE-07) — a failure archiving
 * one document must not roll back or block the other 49 in the same batch.
 */
@Component
public class ArchivalScheduler {

    private static final Logger log = LoggerFactory.getLogger(ArchivalScheduler.class);
    private static final String PRIMARY_REPO_ID = "S3_PRIMARY";
    private static final String ARCHIVE_REPO_ID = "CMOD_ARCHIVE";

    private final TaskRepository taskRepository;
    private final DocRepository docRepository;
    private final RepoInterfaceFactory repoInterfaceFactory;
    private final DistributedLockService distributedLockService;
    private final int batchSize;

    public ArchivalScheduler(TaskRepository taskRepository,
                              DocRepository docRepository,
                              RepoInterfaceFactory repoInterfaceFactory,
                              DistributedLockService distributedLockService,
                              @Value("${dbs.archival.batch-size:50}") int batchSize) {
        this.taskRepository = taskRepository;
        this.docRepository = docRepository;
        this.repoInterfaceFactory = repoInterfaceFactory;
        this.distributedLockService = distributedLockService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "0 15 2 * * *") // 02:15 daily
    public void run() {
        List<Task> batch = taskRepository.findBatchToProcess(TaskType.DOC_ARCHIVAL, PageRequest.of(0, batchSize));
        log.info("ArchivalScheduler: picked up {} DOC_ARCHIVAL tasks (batch size {})", batch.size(), batchSize);

        for (Task task : batch) {
            try {
                processTask(task);
            } catch (Exception e) {
                // A single failed task must not abort the batch (AC-BE-07): log and
                // move on, leaving the task unprocessed for the next run to retry.
                log.error("Failed to archive docId={} (taskId={})", task.getDocId(), task.getId(), e);
            }
        }
    }

    @Transactional("transactionManager")
    public void processTask(Task task) {
        boolean locked = distributedLockService.tryAcquire(task.getDocId(), Duration.ofMinutes(10));
        if (!locked) {
            log.debug("Skipping docId={}, lock held by another pod", task.getDocId());
            return;
        }

        try {
            Doc doc = docRepository.findById(task.getDocId()).orElse(null);
            if (doc == null) {
                log.warn("DOC_ARCHIVAL task references missing docId={}, marking processed", task.getDocId());
                markProcessed(task);
                return;
            }
            if (doc.getArchiveDocUri() != null) {
                log.debug("docId={} already archived, marking task processed", doc.getId());
                markProcessed(task);
                return;
            }

            RepoInterface primaryRepo = repoInterfaceFactory.resolve(PRIMARY_REPO_ID);
            RepoInterface archiveRepo = repoInterfaceFactory.resolve(ARCHIVE_REPO_ID);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            primaryRepo.get(doc.getDocUri().getRepoDocId(), buffer);
            byte[] content = buffer.toByteArray();

            String odDocId;
            try (var in = new java.io.ByteArrayInputStream(content)) {
                odDocId = archiveRepo.archive(doc.getDocUri().getRepoDocId(), in, content.length,
                        contentTypeFor(doc.getFileFormat()));
            }

            doc.setArchiveDocUri(new DocUri(ARCHIVE_REPO_ID, odDocId));
            doc.setDtUpdated(Instant.now());
            docRepository.save(doc);

            markProcessed(task);
            log.info("Archived docId={} to CMOD (odDocId={})", doc.getId(), odDocId);
        } finally {
            distributedLockService.release(task.getDocId());
        }
    }

    private void markProcessed(Task task) {
        task.setProcessed(true);
        task.setDtProcessed(Instant.now());
        taskRepository.save(task);
    }

    private String contentTypeFor(String fileFormat) {
        return switch (fileFormat == null ? "" : fileFormat.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "tiff" -> "image/tiff";
            default -> "application/octet-stream";
        };
    }
}
