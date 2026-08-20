package com.bank.dbs.cqrs;

import com.bank.dbs.entity.Doc;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Listens to the `docs` collection's Change Stream (write-side, primary MongoTemplate)
 * and projects inserts/updates into the `docs_view` read model (read-side,
 * readModelMongoTemplate, secondaryPreferred) — spec 6.1 CQRS Read Model.
 *
 * Resume token handling: on every event we persist the new resume token to
 * change_stream_tokens/docs_listener. On startup we look that document up and pass
 * it as ChangeStreamOptions.resumeToken(...) so a pod restart resumes exactly where
 * it left off (AC-DB-07) instead of re-scanning or skipping events.
 */
@Service
public class ChangeStreamListenerService {

    private static final Logger log = LoggerFactory.getLogger(ChangeStreamListenerService.class);
    private static final String LISTENER_ID = "docs_listener";

    private final MongoTemplate writeSideMongoTemplate;
    private final MongoTemplate readModelMongoTemplate;

    private MessageListenerContainer container;

    public ChangeStreamListenerService(
            @Qualifier("mongoTemplate") MongoTemplate writeSideMongoTemplate,
            @Qualifier("readModelMongoTemplate") MongoTemplate readModelMongoTemplate) {
        this.writeSideMongoTemplate = writeSideMongoTemplate;
        this.readModelMongoTemplate = readModelMongoTemplate;
    }

    @PostConstruct
    public void start() {
        container = new DefaultMessageListenerContainer(writeSideMongoTemplate);
        container.start();

        ChangeStreamOptions options = buildResumeOptions();

        ChangeStreamRequest<Doc> request = ChangeStreamRequest.builder(this::onChangeEvent)
                .collection("docs")
                .changeStreamOptions(options)
                .build();

        container.register(request, Doc.class);
        log.info("ChangeStreamListenerService started, watching 'docs' collection for docs_view sync");
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    private ChangeStreamOptions buildResumeOptions() {
        ChangeStreamToken persisted = writeSideMongoTemplate.findById(LISTENER_ID, ChangeStreamToken.class);
        ChangeStreamOptions.ChangeStreamOptionsBuilder builder = ChangeStreamOptions.builder();
        if (persisted != null && persisted.getResumeToken() != null) {
            builder.resumeToken(persisted.getResumeToken());
        }
        return builder.build();
    }

    private void onChangeEvent(org.springframework.data.mongodb.core.messaging.Message<ChangeStreamDocument<org.bson.Document>, Doc> message) {
        try {
            ChangeStreamDocument<org.bson.Document> raw = message.getRaw();
            if (raw == null) {
                return;
            }

            OperationType opType = raw.getOperationType();
            Doc body = message.getBody();

            if (opType == OperationType.INSERT || opType == OperationType.UPDATE || opType == OperationType.REPLACE) {
                if (body != null) {
                    upsertReadModel(body);
                }
            } else if (opType == OperationType.DELETE) {
                UUID deletedId = extractDeletedId(raw);
                if (deletedId != null) {
                    readModelMongoTemplate.remove(
                            org.springframework.data.mongodb.core.query.Query.query(
                                    org.springframework.data.mongodb.core.query.Criteria.where("_id").is(deletedId)),
                            DocsView.class);
                }
            }

            persistResumeToken(raw.getResumeToken());
        } catch (Exception ex) {
            // A single bad event should not kill the listener; log and continue.
            // Metrics/alerting hook: increment dbs_cqrs_projection_errors_total here.
            log.error("Failed to project change stream event into docs_view", ex);
        }
    }

    private void upsertReadModel(Doc doc) {
        DocsView view = new DocsView();
        view.setId(doc.getId());
        view.setRootDocId(doc.getRootDocId());
        view.setFilename(doc.getFilename());
        view.setDocType(doc.getDocType());
        view.setDocSubType(doc.getDocSubType());
        view.setCustomerId(doc.getCustomerId());
        view.setDocState(doc.getDocState());
        view.setCurrent(doc.isCurrent());
        view.setVersionNumber(doc.getVersionNumber());
        view.setFileSize(doc.getFileSize());
        view.setArchived(doc.getArchiveDocUri() != null);
        view.setDtCreated(doc.getDtCreated());
        view.setDtUpdated(doc.getDtUpdated());

        readModelMongoTemplate.save(view);
    }

    private UUID extractDeletedId(ChangeStreamDocument<org.bson.Document> raw) {
        BsonDocument documentKey = raw.getDocumentKey();
        if (documentKey == null || !documentKey.containsKey("_id")) {
            return null;
        }
        try {
            return UUID.fromString(documentKey.getString("_id").getValue());
        } catch (Exception e) {
            return null;
        }
    }

    private void persistResumeToken(BsonDocument resumeToken) {
        if (resumeToken == null) {
            return;
        }
        writeSideMongoTemplate.save(new ChangeStreamToken(LISTENER_ID, resumeToken));
    }
}
