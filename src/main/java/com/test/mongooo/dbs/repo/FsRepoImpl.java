package com.bank.dbs.repo;

import com.bank.dbs.exception.RepoInstantiationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Shared NFS/EFS (ReadWriteMany PVC) implementation, used for chunk staging and
 * assembly before the final file is pushed to S3 (spec 2.3 "Shared NFS/EFS"), and as
 * a general-purpose FS-backed RepoInterface for lower environments without S3 access.
 */
@Component("fsRepoImpl")
public class FsRepoImpl implements RepoInterface {

    private final Path basePath;

    public FsRepoImpl(@Value("${dbs.storage.base-path}") String basePath) {
        this.basePath = Paths.get(basePath);
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new RepoInstantiationException("FS", e);
        }
    }

    @Override
    public String store(String docId, InputStream content, long contentLength, String contentType) {
        Path target = resolve(docId);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return docId;
        } catch (IOException e) {
            throw new RepoInstantiationException("FS", e);
        }
    }

    @Override
    public void get(String repoDocId, OutputStream destination) {
        try (InputStream in = Files.newInputStream(resolve(repoDocId))) {
            in.transferTo(destination);
        } catch (IOException e) {
            throw new RepoInstantiationException("FS", e);
        }
    }

    @Override
    public InputStream getAsStream(String repoDocId) {
        try {
            return Files.newInputStream(resolve(repoDocId));
        } catch (IOException e) {
            throw new RepoInstantiationException("FS", e);
        }
    }

    @Override
    public void delete(String repoDocId) {
        try {
            Files.deleteIfExists(resolve(repoDocId));
        } catch (IOException e) {
            throw new RepoInstantiationException("FS", e);
        }
    }

    @Override
    public String repoId() {
        return "FS_STAGING";
    }

    /**
     * Path for a chunk's staging file: {basePath}/chunks/{docId}/{chunkIndex}.
     * Used directly by the chunked-upload pipeline rather than through store()/get(),
     * since chunk writes need positional control the RepoInterface contract doesn't
     * expose.
     */
    public Path chunkPath(String docId, int chunkIndex) {
        return basePath.resolve("chunks").resolve(docId).resolve(String.valueOf(chunkIndex));
    }

    public Path chunkStagingDir(String docId) {
        return basePath.resolve("chunks").resolve(docId);
    }

    private Path resolve(String repoDocId) {
        return basePath.resolve(repoDocId);
    }
}
