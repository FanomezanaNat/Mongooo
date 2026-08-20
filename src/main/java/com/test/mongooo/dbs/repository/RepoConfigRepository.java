package com.bank.dbs.repository;

import com.bank.dbs.entity.RepoConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RepoConfigRepository extends MongoRepository<RepoConfig, String> {
    List<RepoConfig> findByActiveTrue();
}
