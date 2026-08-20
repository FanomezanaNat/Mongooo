package com.bank.dbs.entity;

import com.bank.dbs.constant.TaskType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

/**
 * Task queue entry, primarily DOC_ARCHIVAL tasks created via
 * PUT /integration-api/doc-archival-tasks/{docId} (see architecture diagram, section 2).
 * Shard key: {_id: 'hashed'} — spec 6.4.
 *
 * taskData is intentionally a loosely-typed Map because BSON schema flexibility means
 * new task payload shapes don't require a migration (spec 6.1 "Schema Flexibility").
 */
@Document(collection = "tasks")
@CompoundIndexes({
        // Partial index: {taskType:1, processed:1, dtCreated:1} WHERE processed = false
        // Declared here as a plain compound index; the partialFilterExpression is applied
        // in V001InitCollections since Spring Data's annotation model does not expose
        // partial filter expressions directly.
        @CompoundIndex(name = "idx_taskType_processed_dtCreated",
                def = "{'taskType': 1, 'processed': 1, 'dtCreated': 1}")
})
public class Task {

    @Id
    private UUID id;

    @Field("docId")
    private UUID docId;

    @Field("taskType")
    private TaskType taskType;

    @Field("taskData")
    private Map<String, Object> taskData;

    @Field("processed")
    private boolean processed;

    @Field("dtCreated")
    private Instant dtCreated;

    @Field("dtProcessed")
    private Instant dtProcessed;

    public Task() {
    }

    // --- Getters / Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocId() {
        return docId;
    }

    public void setDocId(UUID docId) {
        this.docId = docId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public Map<String, Object> getTaskData() {
        return taskData;
    }

    public void setTaskData(Map<String, Object> taskData) {
        this.taskData = taskData;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public Instant getDtCreated() {
        return dtCreated;
    }

    public void setDtCreated(Instant dtCreated) {
        this.dtCreated = dtCreated;
    }

    public Instant getDtProcessed() {
        return dtProcessed;
    }

    public void setDtProcessed(Instant dtProcessed) {
        this.dtProcessed = dtProcessed;
    }
}
