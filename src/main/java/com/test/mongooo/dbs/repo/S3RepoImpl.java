package com.bank.dbs.repo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Primary long-lived file storage (spec 2.3 "S3 Bucket (dbs-documents)"). Bean name
 * "s3RepoImpl" is referenced by repo_configs.S3_PRIMARY (seeded in V001) and resolved
 * at runtime by RepoInterfaceFactory.
 */
@Component("s3RepoImpl")
public class S3RepoImpl implements RepoInterface {

    private final S3Client s3Client;
    private final String bucket;

    public S3RepoImpl(S3Client s3Client, @Value("${dbs.storage.s3.bucket:dbs-documents}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String store(String docId, InputStream content, long contentLength, String contentType) {
        String key = toKey(docId);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(content, contentLength));
        return key;
    }

    @Override
    public void get(String repoDocId, OutputStream destination) {
        s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(repoDocId).build(),
                ResponseTransformer.toOutputStream(destination));
    }

    @Override
    public InputStream getAsStream(String repoDocId) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(repoDocId).build());
    }

    @Override
    public void delete(String repoDocId) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(repoDocId).build());
    }

    @Override
    public String repoId() {
        return "S3_PRIMARY";
    }

    private String toKey(String docId) {
        // Prefix by first two chars of a fresh UUID for S3 key-space distribution
        // (avoids hot-partitioning on sequential-looking prefixes at high write volume).
        String prefix = UUID.randomUUID().toString().substring(0, 2);
        return "docs/" + prefix + "/" + docId;
    }
}
