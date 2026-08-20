package com.bank.dbs.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a consuming application's own applicationId to a DBS document and tracks
 * replacement history (spec glossary: "tracks replacement history").
 * Shard key: {applicationId: 'hashed'} — spec 6.4.
 */
@Document(collection = "application_docs")
@CompoundIndexes({
        // Merge detection; archival eligibility — covered compound index
        @CompoundIndex(name = "idx_appId_type_subtype_status",
                def = "{'applicationId': 1, 'docType': 1, 'docSubType': 1, 'status': 1}")
})
public class ApplicationDoc {

    @Id
    private UUID id;

    @Field("applicationId")
    private String applicationId;

    @Field("docType")
    private String docType;

    @Field("docSubType")
    private String docSubType;

    /** ACTIVE | REPLACED | SENT_FOR_ARCHIVAL | ARCHIVAL_ALLOWED */
    @Field("status")
    private String status;

    /** Version-aware cross-collection lookup back to docs.rootDocId. */
    @Indexed(name = "idx_rootDocId")
    @Field("rootDocId")
    private UUID rootDocId;

    @Field("docId")
    private UUID docId;

    @Field("dtCreated")
    private Instant dtCreated;

    @Field("dtUpdated")
    private Instant dtUpdated;

    public ApplicationDoc() {
    }

    // --- Getters / Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getRootDocId() {
        return rootDocId;
    }

    public void setRootDocId(UUID rootDocId) {
        this.rootDocId = rootDocId;
    }

    public UUID getDocId() {
        return docId;
    }

    public void setDocId(UUID docId) {
        this.docId = docId;
    }

    public Instant getDtCreated() {
        return dtCreated;
    }

    public void setDtCreated(Instant dtCreated) {
        this.dtCreated = dtCreated;
    }

    public Instant getDtUpdated() {
        return dtUpdated;
    }

    public void setDtUpdated(Instant dtUpdated) {
        this.dtUpdated = dtUpdated;
    }
}
