package com.bank.dbs.service;

import com.bank.dbs.entity.DocLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * tryAcquire / release via the doc_locks collection (spec 5.1 Services table).
 *
 * Acquisition is atomic because it relies on a plain insert() against a collection
 * whose _id *is* the docId being locked: MongoDB's unique index on _id causes a
 * second pod's insert() to throw DuplicateKeyException if the lock is already held,
 * with no need for findAndModify or external Redis infrastructure (spec 2.1 Key
 * Design Decisions: "Concurrency Control").
 *
 * Release only succeeds if the caller's ownerId matches the lock's current owner,
 * preventing a delayed/retried release from a previous holder from clobbering a
 * lock legitimately re-acquired by someone else after TTL expiry (AC-BE-12).
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private final MongoTemplate mongoTemplate;
    private final String ownerId;

    public DistributedLockService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        // Pod-unique owner identity: hostname + a per-JVM random suffix, so two pods
        // never collide even if hostnames are reused (e.g. K8s pod restarts with the
        // same generated name pattern).
        this.ownerId = resolveOwnerId();
    }

    /**
     * Attempts to acquire the lock for docId with the given TTL. Returns true if
     * acquired by this pod, false if another pod currently holds it (i.e. do not
     * treat this as an error condition — the caller should simply skip this doc in
     * the current scheduler pass).
     */
    public boolean tryAcquire(UUID docId, Duration ttl) {
        DocLock lock = new DocLock(docId, ownerId, Instant.now(), Instant.now().plus(ttl));
        try {
            mongoTemplate.insert(lock);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Lock already held for docId={}", docId);
            return false;
        }
    }

    /** Releases the lock only if this pod is still the recorded owner. */
    public void release(UUID docId) {
        Query query = Query.query(Criteria.where("_id").is(docId).and("ownerId").is(ownerId));
        mongoTemplate.remove(query, DocLock.class);
    }

    private String resolveOwnerId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
