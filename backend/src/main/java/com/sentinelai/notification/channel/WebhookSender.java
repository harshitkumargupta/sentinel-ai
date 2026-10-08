package com.sentinelai.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** POSTs a small JSON document to the channel's URL (http/https only; timeout; non-2xx = failure). */
@Component
@RequiredArgsConstructor
public class WebhookSender implements ChannelSender {

    private final NotificationProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public ChannelType type() {
        return ChannelType.WEBHOOK;
    }

    @Override
    public Result send(NotificationChannel channel, Message m) throws Exception {
        URI uri = URI.create(channel.getTarget());
        String body = objectMapper.writeValueAsString(Map.of(
                "source", "SentinelAI", "subject", m.subject(), "text", m.body(),
                "incidentId", m.incidentId() == null ? "" : m.incidentId()));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.getWebhookTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<Void> res = client.send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(props.getWebhookTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("webhook returned HTTP " + res.statusCode());
        }
        return new Result(false, "HTTP " + res.statusCode());
    }
}
