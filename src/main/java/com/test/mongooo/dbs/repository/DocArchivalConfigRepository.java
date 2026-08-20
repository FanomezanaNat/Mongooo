package com.bank.dbs.repository;

import com.bank.dbs.entity.DocArchivalConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DocArchivalConfigRepository extends MongoRepository<DocArchivalConfig, String> {
    Optional<DocArchivalConfig> findByDocTypeAndDocSubType(String docType, String docSubType);
}
