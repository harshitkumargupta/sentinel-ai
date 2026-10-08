package com.sentinelai.notification.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes incident events to channels via the org's notification rules. Runs after the detection
 * transaction commits, on its own executor, so a slow or failing channel can never block or roll
 * back detection/response. Each delivery is retried with exponential backoff and logged
 * (SENT / MOCKED / FAILED) in {@code notification_deliveries}.
 */
@Slf4j
@Service
public class NotificationDispatcher {

    private final NotificationRuleRepository rules;
    private final NotificationChannelRepository channels;
    private final NotificationDeliveryRepository deliveries;
    private final Map<ChannelType, ChannelSender> senders;
    private final NotificationProperties props;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public NotificationDispatcher(NotificationRuleRepository rules, NotificationChannelRepository channels,
                                  NotificationDeliveryRepository deliveries, List<ChannelSender> senderBeans,
                                  NotificationProperties props, ObjectMapper objectMapper,
                                  @Qualifier("notificationExecutor") Executor executor) {
        this.rules = rules;
        this.channels = channels;
        this.deliveries = deliveries;
        this.senders = senderBeans.stream().collect(Collectors.toMap(ChannelSender::type, Function.identity()));
        this.props = props;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * After the detection transaction commits, hand delivery to the notification executor: its own
     * thread and transactions, so retries/backoff never hold up ingestion and a failure can't roll it back.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncident(IncidentNotificationEvent e) {
        try {
            executor.execute(() -> {
                try {
                    dispatch(e);
                } catch (RuntimeException ex) {
                    log.error("Notification dispatch for incident {} failed", e.incidentId(), ex);
                }
            });
        } catch (RuntimeException rejected) {
            log.warn("Notification queue full; dropped notification for incident {}", e.incidentId());
        }
    }

    /** Synchronous core (also used by tests). Returns the deliveries made. */
    public List<NotificationDelivery> dispatch(IncidentNotificationEvent e) {
        String subject = "[SentinelAI] %s incident #%d %s: %s (risk %d)".formatted(e.severity(), e.incidentId(),
                e.created() ? "opened" : "escalated", e.title(), e.riskScore());
        String body = subject + "\nRules: " + String.join(", ", e.ruleTypes()) + "\nOpen /offenses/" + e.incidentId();
        return rules.findByOrgIdAndEnabledTrue(e.orgId()).stream()
                .filter(r -> matches(r, e))
                .flatMap(r -> channelIds(r).stream().map(id -> Map.entry(r, id)))
                .map(pair -> channels.findById(pair.getValue())
                        .filter(c -> c.isEnabled() && c.getOrgId().equals(e.orgId()))
                        .map(c -> deliver(c, pair.getKey().getId(), new ChannelSender.Message(e.orgId(), e.incidentId(), subject, body), false))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    static boolean matches(NotificationRule r, IncidentNotificationEvent e) {
        if (e.severity().ordinal() < r.getMinSeverity().ordinal()) {
            return false;
        }
        if (e.created() ? !r.isOnIncidentCreated() : !r.isOnEscalation()) {
            return false;
        }
        return r.getRuleType() == null || r.getRuleType().isBlank() || e.ruleTypes().contains(r.getRuleType());
    }

    /** Send with retries and exponential backoff; always records the outcome. */
    public NotificationDelivery deliver(NotificationChannel c, Long ruleId, ChannelSender.Message m, boolean test) {
        ChannelSender sender = senders.get(c.getType());
        int attempts = 0;
        String error = null;
        while (attempts < props.getMaxAttempts()) {
            attempts++;
            try {
                ChannelSender.Result r = sender.send(c, m);
                return deliveries.save(NotificationDelivery.builder().orgId(m.orgId()).channelId(c.getId()).ruleId(ruleId)
                        .incidentId(m.incidentId()).subject(truncate(m.subject(), 255)).attempts(attempts).test(test)
                        .status(r.mock() ? NotificationDelivery.Status.MOCKED : NotificationDelivery.Status.SENT).build());
            } catch (Exception ex) {
                error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                log.warn("Channel {} ({}) attempt {}/{} failed: {}", c.getId(), c.getType(), attempts, props.getMaxAttempts(), error);
                if (attempts < props.getMaxAttempts()) {
                    sleep(props.getBackoffMs() * (1L << (attempts - 1)));
                }
            }
        }
        return deliveries.save(NotificationDelivery.builder().orgId(m.orgId()).channelId(c.getId()).ruleId(ruleId)
                .incidentId(m.incidentId()).subject(truncate(m.subject(), 255)).attempts(attempts).test(test)
                .status(NotificationDelivery.Status.FAILED).lastError(truncate(error, 500)).build());
    }

    private List<Long> channelIds(NotificationRule r) {
        try {
            return objectMapper.readValue(r.getChannelIds(), new TypeReference<List<Long>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
