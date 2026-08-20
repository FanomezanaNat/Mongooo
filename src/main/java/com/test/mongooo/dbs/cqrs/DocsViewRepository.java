package com.bank.dbs.cqrs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

/**
 * Read-only repository intended to be backed by readModelMongoTemplate
 * (secondaryPreferred read preference) — see MongoConfig for the two-MongoTemplate
 * bean setup (US-059).
 */
public interface DocsViewRepository extends MongoRepository<DocsView, UUID> {
    Page<DocsView> findByCustomerIdAndDocTypeAndIsCurrentTrue(String customerId, String docType, Pageable pageable);
}
