package com.bank.dbs.repository;

import com.bank.dbs.constant.TaskType;
import com.bank.dbs.entity.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends MongoRepository<Task, UUID> {

    boolean existsByDocIdAndTaskType(UUID docId, TaskType taskType);

    java.util.Optional<Task> findByDocIdAndTaskType(UUID docId, TaskType taskType);

    /**
     * ArchivalScheduler batch source. Uses the partial index idx_taskType_processed_dtCreated
     * (partialFilterExpression: processed = false, applied in V001InitCollections) — the
     * partial filter keeps the index small since only unprocessed tasks are ever queried
     * this way (AC-DB-05).
     */
    @Query(value = "{ 'taskType': ?0, 'processed': false }", sort = "{ 'dtCreated': 1 }")
    List<Task> findBatchToProcess(TaskType taskType, Pageable pageable);
}
