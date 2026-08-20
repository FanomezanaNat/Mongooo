package com.bank.dbs.repository;

import com.bank.dbs.entity.DocLock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface DocLockRepository extends MongoRepository<DocLock, UUID> {
    // Acquisition/release logic lives in DistributedLockService using MongoTemplate
    // directly (insert() to trigger DuplicateKeyException on contention, and a
    // remove() scoped to {_id, ownerId} for release-only-if-owner semantics), since
    // that atomicity guarantee isn't expressible as a derived repository method.
}
