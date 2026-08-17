package com.test.mongooo.model;

import com.test.mongooo.config.database.schema.MongoSchema.Fields;
import org.springframework.data.mongodb.core.mapping.Field;

public class DocUri {
  @Field(Fields.REPO_ID)
  private String repoId;

  @Field(Fields.REPO_DOC_ID)
  private String repoDocId;

}
