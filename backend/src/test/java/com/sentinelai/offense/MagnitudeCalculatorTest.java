package com.sentinelai.offense;

import com.sentinelai.incident.domain.IncidentFeedback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The documented magnitude formula, its components, clamping and weight configuration. */
class MagnitudeCalculatorTest {

    private final MagnitudeCalculator calc = new MagnitudeCalculator(new MagnitudeProperties());

    @Test
    void lowSignalOffense() {
        Magnitude m = calc.calculate(new OffenseFacts(30, false, 0, 1, 1, 1, 0, IncidentFeedback.UNREVIEWED));
        assertThat(m.severity()).isEqualTo(3);
        assertThat(m.relevance()).isEqualTo(2);
        assertThat(m.credibility()).isEqualTo(3);
        assertThat(m.magnitude()).isEqualTo(3); // round((9 + 4 + 3) / 6) = round(2.67)
        assertThat(m.formula()).isEqualTo("round((3*3 + 2*2 + 1*3) / 6) = 3");
    }

    @Test
    void privilegedCorroboratedThreatIntelOffenseIsHighMagnitude() {
        Magnitude m = calc.calculate(new OffenseFacts(85, true, 3, 2, 2, 3, 1, IncidentFeedback.TRUE_POSITIVE));
        assertThat(m.severity()).isEqualTo(9);
        assertThat(m.relevance()).isEqualTo(9);   // 2 + 4 + 3
        assertThat(m.credibility()).isEqualTo(10); // 3 + 2 + 2 + 3 + 2 = 12, clamped
        assertThat(m.magnitude()).isEqualTo(9);   // round((27 + 18 + 10) / 6) = round(9.17)
        assertThat(m.relevanceReasons()).anyMatch(r -> r.contains("privileged"));
        assertThat(m.credibilityReasons()).anyMatch(r -> r.contains("threat-intel"));
    }

    @Test
    void falsePositiveZeroesCredibilityAndCriticalityIsCapped() {
        Magnitude m = calc.calculate(new OffenseFacts(100, false, 9, 3, 3, 3, 2, IncidentFeedback.FALSE_POSITIVE));
        assertThat(m.credibility()).isZero();
        assertThat(m.relevance()).isEqualTo(6); // 2 + min(4, 9)
        assertThat(m.magnitude()).isEqualTo(7); // round((30 + 12 + 0) / 6)
    }

    @Test
    void weightsAreConfigurable() {
        MagnitudeProperties props = new MagnitudeProperties();
        props.setSeverityWeight(1);
        props.setRelevanceWeight(0);
        props.setCredibilityWeight(0);
        Magnitude m = new MagnitudeCalculator(props).calculate(
                new OffenseFacts(70, true, 4, 1, 1, 1, 0, IncidentFeedback.UNREVIEWED));
        assertThat(m.magnitude()).isEqualTo(7); // severity only
    }
}
