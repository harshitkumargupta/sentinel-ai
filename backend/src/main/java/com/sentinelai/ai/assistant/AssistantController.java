package com.sentinelai.ai.assistant;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** "Ask AI" on an incident (any authenticated role; read-only) and the AI mode badge. */
@RestController
@RequiredArgsConstructor
@Tag(name = "AI assistant")
public class AssistantController {

    private final AssistantService assistantService;

    /** Either {@code intent} (one of the fixed questions) or free-text {@code question}. */
    public record AskRequest(AskIntent intent, @Size(max = 300) String question) {
    }

    public record Suggestion(String intent, String label) {
    }

    @PostMapping("/api/incidents/{id}/ask")
    public ApiResponse<AssistantAnswer> ask(@PathVariable Long id, @Valid @RequestBody AskRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(assistantService.ask(id, request.intent(), request.question(), actor));
    }

    @GetMapping("/api/ai/questions")
    public ApiResponse<List<Suggestion>> questions() {
        return ApiResponse.ok(Arrays.stream(AskIntent.values())
                .map(i -> new Suggestion(i.name(), i.label())).toList());
    }

    @GetMapping("/api/ai/status")
    public ApiResponse<AssistantService.AiStatus> status() {
        return ApiResponse.ok(assistantService.status());
    }
}
