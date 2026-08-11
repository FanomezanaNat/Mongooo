package com.test.mongooo;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableFlamingock(
    stages = {@Stage(location = "com.test.mongooo.config.database.migration")}
)
@SpringBootApplication
public class MongoooApplication {

  static void main(String[] args) {
    SpringApplication.run(MongoooApplication.class, args);
  }

}
