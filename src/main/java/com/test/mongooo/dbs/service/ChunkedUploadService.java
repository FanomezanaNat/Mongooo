package com.bank.dbs.service;

import com.bank.dbs.constant.SignedUrlStatus;
import com.bank.dbs.entity.SignedUrl;
import com.bank.dbs.repo.FsRepoImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * US-032, US-037..041: chunk staging to {basepath}/chunks/{docId}/{index}, assembly
 * triggered exactly once across all pods via an atomic Mongo $inc + findAndModify
 * (US-060 — replaces a naive ConcurrentHashMap tracker, which does not work across
 * multiple pods).
 *
 * Concurrency model: every pod that receives a chunk POST calls receiveChunk(),
 * which writes the chunk to shared NFS/EFS (safe from any pod, since chunk N always
 * writes to the same path regardless of which pod handled the request) and then
 * atomically increments signed_urls.receivedChunks. findAndModify returns the
 * post-increment document; only the pod that observes
 * receivedChunks == totalChunks proceeds to assembleAndCleanup() — guaranteeing
 * assembly runs exactly once (AC-BE-06).
 */
@Service
public class ChunkedUploadService {

    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);

    private final FsRepoImpl fsRepo;
    private final MongoTemplate mongoTemplate;

    public ChunkedUploadService(FsRepoImpl fsRepo, MongoTemplate mongoTemplate) {
        this.fsRepo = fsRepo;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Writes one chunk to disk and atomically increments the received-chunk counter.
     * Returns true if this call was the one that completed the set (i.e. this pod
     * should proceed to assemble).
     */
    public boolean receiveChunk(UUID signedUrlDocId, String docId, InputStream chunkData, int chunkIndex, int totalChunks) {
        Path chunkPath = fsRepo.chunkPath(docId, chunkIndex);
        try {
            Files.createDirectories(chunkPath.getParent());
            Files.copy(chunkData, chunkPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stage chunk " + chunkIndex + " for docId=" + docId, e);
        }

        Query query = Query.query(Criteria.where("_id").is(signedUrlDocId));
        Update update = new Update().inc("receivedChunks", 1).set("status", SignedUrlStatus.IN_PROGRESS.name());
        SignedUrl updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), SignedUrl.class);

        boolean isComplete = updated != null && updated.getReceivedChunks() == totalChunks;
        if (isComplete) {
            log.info("All {} chunks received for docId={}; this pod will assemble", totalChunks, docId);
        }
        return isComplete;
    }

    /**
     * Concatenates all staged chunks (in index order) into a single assembled file
     * on the same shared mount, then removes the per-chunk staging files. The
     * assembled path is returned for FileValidationService + RepoInterface.store().
     */
    public Path assembleAndCleanup(String docId, int totalChunks) {
        Path assembledPath = fsRepo.chunkStagingDir(docId).resolveSibling("assembled").resolve(docId);
        try {
            Files.createDirectories(assembledPath.getParent());
            try (OutputStream out = Files.newOutputStream(assembledPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkPath = fsRepo.chunkPath(docId, i);
                    try (InputStream in = Files.newInputStream(chunkPath)) {
                        in.transferTo(out);
                    }
                }
            }
            cleanupStagingDir(docId);
            return assembledPath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to assemble chunks for docId=" + docId, e);
        }
    }

    private void cleanupStagingDir(String docId) throws IOException {
        Path stagingDir = fsRepo.chunkStagingDir(docId);
        if (!Files.exists(stagingDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(stagingDir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to clean up staging path {}", p, e);
                }
            });
        }
    }
}
