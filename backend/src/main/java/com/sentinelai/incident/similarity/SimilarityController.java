package com.sentinelai.incident.similarity;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Similar incidents")
public class SimilarityController {

    private final SimilarityService similarityService;

    @GetMapping("/api/incidents/{id}/similar")
    public ApiResponse<List<SimilarIncident>> similar(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "5") int limit,
                                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(similarityService.findSimilar(id, actor, Math.min(limit, 20)));
    }
}
