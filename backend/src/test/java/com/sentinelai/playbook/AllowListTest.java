package com.sentinelai.playbook;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.playbook.domain.PlaybookAction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A destructive action type not on the allow-list can never execute. */
@SpringBootTest(properties = {"sentinel.ai.enabled=false", "sentinel.kafka.enabled=false",
        "sentinel.playbook.allowed-actions=disable_user,add_watchlist"})
@ActiveProfiles("test")
class AllowListTest extends PlaybookTestSupport {

    @Test
    void blockIpRefusedWhenNotAllowListed() {
        PlaybookAction a = propose("block_ip", "203.0.113.50", Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        assertThatThrownBy(() -> playbookService.execute(a.getId(), principal(analyst)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("allow-list");
        org.assertj.core.api.Assertions.assertThat(firewall.isBlocked("203.0.113.50")).isFalse();
    }
}
