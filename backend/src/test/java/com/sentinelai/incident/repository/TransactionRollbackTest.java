package com.sentinelai.incident.repository;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicitly demonstrates transactional rollback: a row inserted inside a transaction that is
 * rolled back must not be visible in a subsequent transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TransactionRollbackTest {

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void rolledBackInsertIsNotPersisted() {
        long before = incidentRepository.count();

        incidentRepository.saveAndFlush(Incident.builder()
                .title("Doomed incident")
                .status(IncidentStatus.OPEN)
                .severity(Severity.LOW)
                .build());

        // Visible within the current transaction...
        assertThat(incidentRepository.count()).isEqualTo(before + 1);

        // ...but roll the transaction back and start a fresh one.
        TestTransaction.flagForRollback();
        TestTransaction.end();

        TestTransaction.start();
        assertThat(incidentRepository.count()).isEqualTo(before);
    }
}
