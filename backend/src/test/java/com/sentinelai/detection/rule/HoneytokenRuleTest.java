package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.detection.support.FakeRuleContext;
import com.sentinelai.detection.support.RuleTestFixtures;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoneytokenRuleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void firesCriticalAndBumpsCountOnMatch() {
        HoneytokenRepository repo = mock(HoneytokenRepository.class);
        Honeytoken token = Honeytoken.builder().id(7L).org(RuleTestFixtures.ORG)
                .type("AWS_KEY").valueHash(Hashing.sha256Hex("secret")).triggeredCount(0).build();
        lenient().when(repo.findFirstByOrg_IdAndValueHash(anyLong(), anyString())).thenReturn(Optional.empty());
        when(repo.findFirstByOrg_IdAndValueHash(anyLong(), eq(Hashing.sha256Hex("secret")))).thenReturn(Optional.of(token));

        HoneytokenRule rule = new HoneytokenRule(repo, mapper);
        var def = RuleTestFixtures.rule("HONEYTOKEN", "{}", Severity.HIGH, "T1078.001");
        SecurityEvent event = RuleTestFixtures.event(EventType.HONEYTOKEN_ACCESS)
                .rawPayload("{\"value\":\"secret\"}").build();

        var draft = rule.evaluate(event, def, new FakeRuleContext());
        assertThat(draft).isPresent();
        assertThat(draft.get().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(token.getTriggeredCount()).isEqualTo(1);
        verify(repo).save(token);
    }

    @Test
    void doesNotFireWhenNoHoneytokenMatches() {
        HoneytokenRepository repo = mock(HoneytokenRepository.class);
        when(repo.findFirstByOrg_IdAndValueHash(anyLong(), anyString())).thenReturn(Optional.empty());

        HoneytokenRule rule = new HoneytokenRule(repo, mapper);
        var def = RuleTestFixtures.rule("HONEYTOKEN", "{}", Severity.HIGH, "T1078.001");
        SecurityEvent event = RuleTestFixtures.event(EventType.HONEYTOKEN_ACCESS)
                .rawPayload("{\"value\":\"not-a-decoy\"}").build();

        assertThat(rule.evaluate(event, def, new FakeRuleContext())).isEmpty();
    }
}
