package com.test.mongooo.X.config;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.event.ValidatingMongoEventListener;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * US-059: two MongoTemplate beans.
 *  - "mongoTemplate" (@Primary): write side, WriteConcern MAJORITY + journal=true
 *    (AC-DB-09 — data written before failover is readable after new primary election).
 *    Used by DocService, VersionService and all @Transactional writers.
 *  - "readModelMongoTemplate": read side, ReadPreference secondaryPreferred, used
 *    exclusively by DocsView / dashboard listing queries so heavy list/pagination
 *    traffic never contends with the primary's write path.
 *
 * MongoTransactionManager is bound to the primary factory since multi-document
 * transactions (VersionService.createNewVersion()) must run against the primary.
 */
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:dbs}")
    private String databaseName;

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    @Primary
    public MongoDatabaseFactory primaryMongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
    }

    @Bean(name = "mongoTemplate")
    @Primary
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
        MongoTemplate template = new MongoTemplate(factory);
        // WriteConcern MAJORITY + journal=true is set at the MongoClient URI level
        // (see application.yml: retryWrites=true&w=majority&journal=true); the
        // explicit setWriteConcern below documents the requirement at the code level
        // as a defence-in-depth measure.
        template.setWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        return template;
    }

    @Bean(name = "readModelMongoTemplate")
    public MongoTemplate readModelMongoTemplate(MongoClient mongoClient) {
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
        MongoTemplate template = new MongoTemplate(factory);
        template.setReadPreference(ReadPreference.secondaryPreferred());
        return template;
    }

    @Bean
    public PlatformTransactionManager transactionManager(MongoDatabaseFactory primaryMongoDatabaseFactory) {
        return new MongoTransactionManager(primaryMongoDatabaseFactory);
    }

    @Bean
    public ValidatingMongoEventListener validatingMongoEventListener() {
        return new ValidatingMongoEventListener(localValidatorFactoryBean().getValidator());
    }

    @Bean
    public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean localValidatorFactoryBean() {
        return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
    }
}
