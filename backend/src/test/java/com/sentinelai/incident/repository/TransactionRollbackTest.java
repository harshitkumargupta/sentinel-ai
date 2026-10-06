package com.sentinelai.incident.repository;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicitly demonstrates transactional rollback: a row inserted in a transaction that is
 * rolled back must not be visible in a subsequent transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TransactionRollbackTest {

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void rolledBackInsertIsNotPersisted() {
        Organization org = organizationRepository.findById(1L).orElseThrow();
        long before = incidentRepository.count();

        incidentRepository.saveAndFlush(Incident.builder()
                .org(org)
                .title("Doomed incident")
                .status(IncidentStatus.OPEN)
                .severity(Severity.LOW)
                .feedback(IncidentFeedback.UNREVIEWED)
                .build());

        assertThat(incidentRepository.count()).isEqualTo(before + 1);

        TestTransaction.flagForRollback();
        TestTransaction.end();

        TestTransaction.start();
        assertThat(incidentRepository.count()).isEqualTo(before);
    }
}
