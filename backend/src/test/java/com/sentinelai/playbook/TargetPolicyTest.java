package com.sentinelai.playbook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** IPv4 CIDR containment used to protect internal networks from auto-blocking. */
class TargetPolicyTest {

    @Test
    void matchesInternalRangesAndRejectsPublic() {
        assertThat(TargetPolicy.ipInCidr("10.0.0.5", "10.0.0.0/8")).isTrue();
        assertThat(TargetPolicy.ipInCidr("172.16.5.5", "172.16.0.0/12")).isTrue();
        assertThat(TargetPolicy.ipInCidr("192.168.1.1", "192.168.0.0/16")).isTrue();
        assertThat(TargetPolicy.ipInCidr("127.0.0.1", "127.0.0.0/8")).isTrue();

        assertThat(TargetPolicy.ipInCidr("203.0.113.5", "10.0.0.0/8")).isFalse();
        assertThat(TargetPolicy.ipInCidr("11.0.0.1", "10.0.0.0/8")).isFalse();
        assertThat(TargetPolicy.ipInCidr("not-an-ip", "10.0.0.0/8")).isFalse();
    }
}
