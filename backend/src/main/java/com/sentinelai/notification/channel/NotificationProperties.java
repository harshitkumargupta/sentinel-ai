package com.sentinelai.notification.channel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Notification delivery: retries/backoff, webhook timeout and optional SMTP. With no SMTP host,
 * email channels are a logged "mock" — in-app delivery always works.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.notifications")
public class NotificationProperties {

    @Min(1) @Max(10)
    private int maxAttempts = 3;

    @Min(0)
    private long backoffMs = 500;

    @Min(100)
    private int webhookTimeoutMs = 3000;

    private final Smtp smtp = new Smtp();

    @Getter
    @Setter
    public static class Smtp {
        /** Empty = no SMTP; email channels become mock (logged) deliveries. */
        private String host = "";
        private int port = 587;
        private String username = "";
        /** Only from the environment (SMTP_PASSWORD) — never committed. */
        private String password = "";
        private String from = "sentinel@localhost";
        private boolean starttls = true;
    }
}
