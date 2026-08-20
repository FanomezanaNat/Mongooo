package com.bank.dbs.repository;

import com.bank.dbs.entity.ApplicationDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationDocRepository extends MongoRepository<ApplicationDoc, UUID> {

    /** Covered by idx_appId_type_subtype_status. */
    List<ApplicationDoc> findByApplicationIdAndDocTypeAndDocSubTypeAndStatus(
            String applicationId, String docType, String docSubType, String status);

    List<ApplicationDoc> findByApplicationId(String applicationId);

    /** Version-aware cross-collection lookup — uses idx_rootDocId. */
    Optional<ApplicationDoc> findByRootDocId(UUID rootDocId);
}
