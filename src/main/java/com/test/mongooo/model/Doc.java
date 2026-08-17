package com.test.mongooo.model;

import com.test.mongooo.config.database.schema.MongoSchema.Collections;
import com.test.mongooo.config.database.schema.MongoSchema.Fields;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = Collections.DOCS)
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Doc {

  @Id
  private UUID id;

  @CreatedDate
  @Field(Fields.DT_CREATED)
  private Instant dtCreated;

  @LastModifiedDate
  @Field(Fields.DT_UPDATED)
  private Instant dtUpdated;

  @Field(Fields.DOC_URI)
  private DocUri docUri;

  /**
   * Sparse index: excludes docs where archival has not yet run (AC-DB — smaller index, only indexes
   * documents where the field actually exists). Non-null blocks deletion (AC-BE-09: DELETE returns
   * 403 ARCHIVED, not 404).
   */
  @Field(Fields.ARCHIVE_DOC_URI)
  private DocUri archiveDocUri;

  @Field(Fields.DOC_STATE)
  private DocState docState;

  @Field(Fields.FILENAME)
  private String filename;

  @Field(Fields.FILE_SIZE)
  private Long fileSize;

  /**
   * pdf, jpeg, png, tiff
   */
  @Field(Fields.FILE_FORMAT)
  private String fileFormat;

  @Field(Fields.DOC_TYPE)
  private String docType;

  @Field(Fields.DOC_SUB_TYPE)
  private String docSubType;

  /**
   * Shard key for this collection ({customerId: 'hashed'}) — spec 6.4.
   */
  @Field(Fields.CUSTOMER_ID)
  private String customerId;

  @Field(Fields.ROOT_DOC_ID)
  private UUID rootDocId;

  @Field(Fields.VERSION_NUMBER)
  private int versionNumber;

  @Field(Fields.IS_CURRENT)
  private boolean isCurrent;

  @Field(Fields.PREVIOUS_VERSION_ID)
  private UUID previousVersionId;

  /**
   * Spring Data optimistic-lock field. Backfilled by V004SchemaVersionBackfill.
   */
  @Version
  @Field(Fields.SCHEMA_VERSION)
  private Integer schemaVersion;

}
