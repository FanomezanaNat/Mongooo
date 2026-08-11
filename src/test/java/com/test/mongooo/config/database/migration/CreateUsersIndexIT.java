package com.test.mongooo.config.database.migration;

import static io.flamingock.support.domain.AuditEntryDefinition.APPLIED;

import com.test.mongooo.config.database.FlamingockAuditConfig;
import com.test.mongooo.config.database.MongoConfigConnection;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTest;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

@FlamingockSpringBootTest
@Import({MongoConfigConnection.class, FlamingockAuditConfig.class})
@ActiveProfiles("test")
class CreateUsersIndexIT {


  @Autowired
  private FlamingockSpringBootTestSupport testSupport;
  @Autowired
  private MongoTemplate mongoTemplate;
  @BeforeEach
  void cleanDatabase() {
    mongoTemplate.getDb().drop();
  }

  @Test
  @DisplayName("Devrait exécuter avec succès le script de création d'index")
  void shouldExecuteChanges() {
    testSupport
        .givenBuilderFromContext()
        .whenRun()
        .thenExpectAuditFinalStateSequence(
            APPLIED(_0001__CreateUsersIndex.class)
        )
        .verify();
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