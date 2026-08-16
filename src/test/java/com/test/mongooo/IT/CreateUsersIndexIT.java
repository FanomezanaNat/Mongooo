package com.test.mongooo.IT;

import static io.flamingock.support.domain.AuditEntryDefinition.APPLIED;
import static org.assertj.core.api.Assertions.assertThat;

import com.test.mongooo.AbstractFlamingockIntegrationTest;
import com.test.mongooo.config.database.migration._0001__CreateUsersIndex;
import com.test.mongooo.config.database.schema.MongoSchema;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;


class CreateUsersIndexIT extends AbstractFlamingockIntegrationTest {

  @Autowired
  private FlamingockSpringBootTestSupport testSupport;

  @Autowired
  private MongoTemplate mongoTemplate;

  @BeforeEach
  void cleanDatabase() {
    mongoTemplate.getDb()
        .drop();
  }

  @Test
  @DisplayName("Devrait exécuter avec succès le script de création d'index et l'initialisation des données")
  void shouldExecuteChanges() {

    testSupport.givenBuilderFromContext()
        .whenRun()
        .thenExpectAuditFinalStateSequence(
            APPLIED(_0001__CreateUsersIndex.class)
        )
        .verify();

    var docsIndexOps = mongoTemplate.indexOps(MongoSchema.Collections.DOCS);
    var docsIndexes = docsIndexOps.getIndexInfo();

    assertThat(docsIndexes)
        .as("L'index sur customerId devrait être présent")
        .anyMatch(index -> index.isIndexForFields(List.of(MongoSchema.Fields.CUSTOMER_ID)));

    long repoCount = mongoTemplate.getCollection(MongoSchema.Collections.REPOSITORIES)
        .countDocuments();
    long configCount = mongoTemplate.getCollection(MongoSchema.Collections.DOC_ARCHIVAL_CONFIG)
        .countDocuments();

    assertThat(repoCount)
        .as("La collection 'repositories' devrait contenir les données de seed")
        .isGreaterThan(0);

    assertThat(configCount)
        .as("La collection 'doc_archival_config' devrait contenir la configuration par défaut")
        .isGreaterThan(0);
  }

  @Test
  @DisplayName("Devrait ignorer la migration si elle a déjà été exécutée précédemment")
  void shouldSkipAlreadyAppliedChanges() {

    testSupport
        .givenBuilderFromContext()
        .andExistingAudit(
            APPLIED(_0001__CreateUsersIndex.class)
        )
        .whenRun()
        .thenExpectAuditFinalStateSequence(
            APPLIED(_0001__CreateUsersIndex.class)
        )
        .verify();
  }

}