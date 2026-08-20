package com.bank.dbs.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Per docType/docSubType archival eligibility configuration, seeded by
 * V001InitCollections with the 5 entries called out in spec 6.5:
 * KYC/PASSPORT, KYC/UTILITY_BILL, TRADE_FINANCE/LC, TRADE_FINANCE/INVOICE,
 * ONBOARDING/APPLICATION_FORM.
 */
@Document(collection = "doc_archival_config")
public class DocArchivalConfig {

    @Id
    private String id; // "{docType}:{docSubType}"

    @Field("docType")
    private String docType;

    @Field("docSubType")
    private String docSubType;

    @Field("archivalEnabled")
    private boolean archivalEnabled = true;

    @Field("targetRepoId")
    private String targetRepoId = "CMOD_ARCHIVE";

    public DocArchivalConfig() {
    }

    public DocArchivalConfig(String docType, String docSubType) {
        this.id = docType + ":" + docSubType;
        this.docType = docType;
        this.docSubType = docSubType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocSubType() {
        return docSubType;
    }

    public void setDocSubType(String docSubType) {
        this.docSubType = docSubType;
    }

    public boolean isArchivalEnabled() {
        return archivalEnabled;
    }

    public void setArchivalEnabled(boolean archivalEnabled) {
        this.archivalEnabled = archivalEnabled;
    }

    public String getTargetRepoId() {
        return targetRepoId;
    }

    public void setTargetRepoId(String targetRepoId) {
        this.targetRepoId = targetRepoId;
    }
}
