package com.bank.dbs.entity;

import com.bank.dbs.constant.DocState;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

/**
 * Primary document metadata record. See spec section 6.2 (Document Collection Schema)
 * and 6.3 (Index Strategy) / 6.4 (Sharding Strategy: {customerId: 'hashed'}).
 *
 * IMPORTANT invariants (enforced by VersionService, not by this class):
 *  - Exactly one Doc per rootDocId chain has isCurrent = true at any point in time.
 *  - rootDocId == _id for the first version in a chain.
 *  - schemaVersion is the Spring Data @Version field used for optimistic locking
 *    on updateMetadata() (AC-BE-13).
 */
@Document(collection = "docs")
@CompoundIndexes({
        // O(log n) current-version lookup — AC-DB-02
        @CompoundIndex(name = "idx_rootDocId_isCurrent", def = "{'rootDocId': 1, 'isCurrent': 1}"),
        // Version history listing ordered by number
        @CompoundIndex(name = "idx_rootDocId_versionNumber", def = "{'rootDocId': 1, 'versionNumber': 1}"),
        // Merge candidate detection; archival config lookup
        @CompoundIndex(name = "idx_customer_type_subtype_current",
                def = "{'customerId': 1, 'docType': 1, 'docSubType': 1, 'isCurrent': 1}"),
        // PurgeScheduler: PENDING docs older than retention window
        @CompoundIndex(name = "idx_docState_dtCreated", def = "{'docState': 1, 'dtCreated': 1}")
})
public class Doc {

    @Id
    private UUID id;

    @CreatedDate
    @Field("dtCreated")
    private Instant dtCreated;

    @LastModifiedDate
    @Field("dtUpdated")
    private Instant dtUpdated;

    @Field("docUri")
    private DocUri docUri;

    /**
     * Sparse index: excludes docs where archival has not yet run (AC-DB — smaller index,
     * only indexes documents where the field actually exists). Non-null blocks deletion
     * (AC-BE-09: DELETE returns 403 ARCHIVED, not 404).
     */
    @Indexed(name = "idx_archiveDocUri_sparse", sparse = true)
    @Field("archiveDocUri")
    private DocUri archiveDocUri;

    @Field("docState")
    private DocState docState;

    @Field("filename")
    private String filename;

    @Field("fileSize")
    private long fileSize;

    /** pdf, jpeg, png, tiff */
    @Field("fileFormat")
    private String fileFormat;

    @Field("docType")
    private String docType;

    @Field("docSubType")
    private String docSubType;

    /** Shard key for this collection ({customerId: 'hashed'}) — spec 6.4. */
    @Field("customerId")
    private String customerId;

    @Field("rootDocId")
    private UUID rootDocId;

    @Field("versionNumber")
    private int versionNumber;

    @Field("isCurrent")
    private boolean isCurrent;

    @Field("previousVersionId")
    private UUID previousVersionId;

    /** Spring Data optimistic-lock field. Backfilled by V004SchemaVersionBackfill. */
    @Version
    @Field("schemaVersion")
    private Long schemaVersion;

    public Doc() {
    }

    // --- Getters / Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public DocUri getDocUri() {
        return docUri;
    }

    public void setDocUri(DocUri docUri) {
        this.docUri = docUri;
    }

    public DocUri getArchiveDocUri() {
        return archiveDocUri;
    }

    public void setArchiveDocUri(DocUri archiveDocUri) {
        this.archiveDocUri = archiveDocUri;
    }

    public DocState getDocState() {
        return docState;
    }

    public void setDocState(DocState docState) {
        this.docState = docState;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
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

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public UUID getRootDocId() {
        return rootDocId;
    }

    public void setRootDocId(UUID rootDocId) {
        this.rootDocId = rootDocId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public UUID getPreviousVersionId() {
        return previousVersionId;
    }

    public void setPreviousVersionId(UUID previousVersionId) {
        this.previousVersionId = previousVersionId;
    }

    public Long getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Long schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
