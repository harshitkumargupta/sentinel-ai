package com.sentinelai.notification.channel;

/** Delivers one message on one channel type. Throws on failure (the dispatcher retries). */
public interface ChannelSender {

    record Message(Long orgId, Long incidentId, String subject, String body) {
    }

    /** {@code mock=true}: nothing left the system (no SMTP configured) — the message was only logged. */
    record Result(boolean mock, String detail) {
    }

    ChannelType type();

    Result send(NotificationChannel channel, Message message) throws Exception;
}
