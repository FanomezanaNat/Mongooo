package com.bank.dbs.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Seed-configured repo implementations (S3_PRIMARY, CMOD_ARCHIVE — seeded by
 * V001InitCollections). Resolved at runtime by RepoInterfaceFactory to pick the
 * concrete RepoInterface bean (S3RepoImpl / CmodRepoImpl / FsRepoImpl).
 */
@Document(collection = "repo_configs")
public class RepoConfig {

    @Id
    private String repoId; // e.g. "S3_PRIMARY", "CMOD_ARCHIVE"

    @Field("repoType")
    private String repoType; // S3 | CMOD | FS

    @Field("beanName")
    private String beanName; // Spring bean name resolved via reflection

    @Field("container")
    private String container; // bucket name / CMOD application group / base path

    @Field("active")
    private boolean active = true;

    public RepoConfig() {
    }

    public String getRepoId() {
        return repoId;
    }

    public void setRepoId(String repoId) {
        this.repoId = repoId;
    }

    public String getRepoType() {
        return repoType;
    }

    public void setRepoType(String repoType) {
        this.repoType = repoType;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
