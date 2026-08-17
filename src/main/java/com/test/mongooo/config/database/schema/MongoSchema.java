package com.test.mongooo.config.database.schema;

public final class MongoSchema {

  private MongoSchema() {
  }

  public static final class Collections {
    public static final String DOCS = "docs";
    public static final String TASKS = "tasks";
    public static final String SIGNED_URLS = "signed_urls";
    public static final String APPLICATION_DOCS = "application_docs";
    public static final String DOC_LOCKS = "doc_locks";
    public static final String REPOSITORIES = "repositories";
    public static final String DOC_ARCHIVAL_CONFIG = "doc_archival_config";
    private Collections() {
    }
  }

  public static final class Fields {
    public static final String ID = "_id";
    public static final String DT_CREATED = "dtCreated";
    public static final String DT_UPDATED = "dtUpdated";

    // Champs de la collection 'docs'
    public static final String DOC_URI = "docUri";
    public static final String ARCHIVE_DOC_URI = "archiveDocUri";
    public static final String DOC_STATE = "docState";
    public static final String FILENAME = "filename";
    public static final String FILE_SIZE = "fileSize";
    public static final String FILE_FORMAT = "fileFormat";
    public static final String DOC_TYPE = "docType";
    public static final String DOC_SUB_TYPE = "docSubType";
    public static final String CUSTOMER_ID = "customerId";
    public static final String ROOT_DOC_ID = "rootDocId";
    public static final String VERSION_NUMBER = "versionNumber";
    public static final String IS_CURRENT = "isCurrent";
    public static final String PREVIOUS_VERSION_ID = "previousVersionId";
    public static final String SCHEMA_VERSION = "schemaVersion";

    // Champs de la collection 'tasks'
    public static final String TASK_TYPE = "taskType";
    public static final String PROCESSED = "processed";

    // Champs de la collection 'signed_urls'
    public static final String TOKEN_HASH = "tokenHash";
    public static final String STATUS = "status";
    public static final String EXPIRES_AT = "expiresAt";

    // Champs de la collection 'application_docs'
    public static final String APPLICATION_ID = "applicationId";

    // Champs des collections référentielles (repositories / doc_archival_config)
    public static final String REPO_ID = "repoId";
    public static final String REPO_DOC_ID = "repoDocId";
    public static final String TYPE = "type";
    public static final String ENABLED = "enabled";
    private Fields() {
    }
  }

}
