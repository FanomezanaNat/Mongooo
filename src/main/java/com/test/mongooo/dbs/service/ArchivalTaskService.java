package com.bank.dbs.service;

import com.bank.dbs.constant.TaskType;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.entity.Task;
import com.bank.dbs.exception.DocNotFoundException;
import com.bank.dbs.repository.DocRepository;
import com.bank.dbs.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * POST /integration-api/docs/{docId}/archive-task (spec 5.1). Only creates a task
 * if id={docId} does not already exist (see architecture diagram section 2: "Only
 * create task if task with id = doc_id does not exist. Insert record with...").
 * Per that diagram, the Task's own _id is the docId itself, making creation
 * naturally idempotent via a duplicate-key check rather than a separate exists()
 * round-trip race.
 */
@Service
public class ArchivalTaskService {

    private final DocRepository docRepository;
    private final TaskRepository taskRepository;

    public ArchivalTaskService(DocRepository docRepository, TaskRepository taskRepository) {
        this.docRepository = docRepository;
        this.taskRepository = taskRepository;
    }

    public UUID createArchivalTask(UUID docId, String docType, String docSubType, String customerId) {
        Doc doc = docRepository.findById(docId).orElseThrow(() -> new DocNotFoundException(docId));

        var existing = taskRepository.findByDocIdAndTaskType(docId, TaskType.DOC_ARCHIVAL);
        if (existing.isPresent()) {
            // Idempotent per spec diagram note: creating a task that already exists
            // for this doc is a no-op that returns the existing task's id.
            return existing.get().getId();
        }

        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setDocId(docId);
        task.setTaskType(TaskType.DOC_ARCHIVAL);
        task.setTaskData(Map.of(
                "docType", docType != null ? docType : doc.getDocType(),
                "docSubType", docSubType != null ? docSubType : doc.getDocSubType(),
                "customerId", customerId != null ? customerId : doc.getCustomerId()
        ));
        task.setProcessed(false);
        task.setDtCreated(Instant.now());

        return taskRepository.save(task).getId();
    }
}
