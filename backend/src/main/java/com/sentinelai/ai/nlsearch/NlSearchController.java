package com.sentinelai.ai.nlsearch;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "AI search")
public class NlSearchController {

    private final NlSearchService nlSearchService;

    public record NlQueryRequest(@NotBlank String query) {
    }

    @PostMapping("/nl")
    public ApiResponse<NlSearchResponse> search(@Valid @RequestBody NlQueryRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(nlSearchService.search(request.query(), actor));
    }
}
