package com.test.mongooo.config.database.connection;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfigConnection {

  @Bean
  public MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String connectionString) {
    if (connectionString == null || connectionString.isBlank()) {
      throw new IllegalStateException("La variable d'environnement MONGO_URI n'est pas définie !");
    }

    var settings = MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(connectionString))
        .build();

    return MongoClients.create(settings);
  }
  @Bean
  public MongoDatabaseFactory mongoDatabaseFactory(
      MongoClient mongoClient,
      @Value("${spring.data.mongodb.database}") String databaseName) {
    if (databaseName == null || databaseName.isBlank()) {
      throw new IllegalStateException("La variable d'environnement MONGO_DATABASE n'est pas définie !");
    }
    return new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
  }



}
