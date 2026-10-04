package com.sentinelai.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI-compatible chat-completions client. The API key is read only from the {@code LLM_API_KEY}
 * environment variable and is never logged or echoed. Prompts and keys are kept out of logs; only
 * model name, latency and token counts are recorded (by the guarded wrapper).
 */
@Slf4j
public class HttpLlmClient implements LlmClient {

    public static final String API_KEY_ENV = "LLM_API_KEY";

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final String apiKey;

    public HttpLlmClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                .build();
        this.apiKey = System.getenv(API_KEY_ENV);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmUnavailableException(API_KEY_ENV + " is not set");
        }
        long t0 = System.currentTimeMillis();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", props.getModel());
            body.put("max_tokens", request.maxTokens());
            body.put("temperature", 0);
            body.putObject("response_format").put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.systemPrompt());
            messages.addObject().put("role", "user").put("content", request.userPrompt());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new LlmUnavailableException("LLM HTTP " + resp.statusCode());
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            String model = root.path("model").asText(props.getModel());
            return new LlmResponse(content, model, promptTokens, completionTokens,
                    System.currentTimeMillis() - t0);
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Never include the request body (prompt) or key in the log.
            throw new LlmUnavailableException("LLM call failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
