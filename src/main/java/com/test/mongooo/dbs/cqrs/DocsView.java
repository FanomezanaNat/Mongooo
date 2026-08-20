package com.bank.dbs.cqrs;

import com.bank.dbs.constant.DocState;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

/**
 * Denormalised CQRS read model, kept eventually-consistent with `docs` via
 * {@link ChangeStreamListenerService}. Queried through DashboardComponent's
 * listDocuments() path — reads always go against readModelMongoTemplate
 * (secondaryPreferred), writes never touch this collection directly (AC-DB-10:
 * reflects doc inserts within 200ms P95).
 */
@Document(collection = "docs_view")
@CompoundIndexes({
        @CompoundIndex(name = "idx_view_customer_type_current",
                def = "{'customerId': 1, 'docType': 1, 'isCurrent': 1}"),
        @CompoundIndex(name = "idx_view_dtCreated", def = "{'dtCreated': -1}")
})
public class DocsView {

    @Id
    private UUID id; // mirrors docs._id

    @Field("rootDocId")
    private UUID rootDocId;

    @Field("filename")
    private String filename;

    @Field("docType")
    private String docType;

    @Field("docSubType")
    private String docSubType;

    @Field("customerId")
    private String customerId;

    @Field("docState")
    private DocState docState;

    @Field("isCurrent")
    private boolean isCurrent;

    @Field("versionNumber")
    private int versionNumber;

    @Field("fileSize")
    private long fileSize;

    @Field("archived")
    private boolean archived;

    @Field("dtCreated")
    private Instant dtCreated;

    @Field("dtUpdated")
    private Instant dtUpdated;

    public DocsView() {
    }

    // --- Getters / Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRootDocId() {
        return rootDocId;
    }

    public void setRootDocId(UUID rootDocId) {
        this.rootDocId = rootDocId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
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

    public DocState getDocState() {
        return docState;
    }

    public void setDocState(DocState docState) {
        this.docState = docState;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        isCurrent = current;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
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
