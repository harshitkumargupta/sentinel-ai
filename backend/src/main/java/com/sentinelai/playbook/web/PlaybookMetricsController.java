package com.sentinelai.playbook.web;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response-time evaluation: time from an incident being raised (alert) to its first approved
 * response action — a SOAR MTTR for the feedback/eval harness.
 */
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
@Tag(name = "Response evaluation")
public class PlaybookMetricsController {

    public record ResponseTimeMetrics(long approvedActions, double medianSeconds, double avgSeconds) {
    }

    private final PlaybookActionRepository repository;

    @GetMapping("/response-time")
    @Transactional(readOnly = true)
    public ApiResponse<ResponseTimeMetrics> responseTime(@AuthenticationPrincipal AppUserPrincipal actor) {
        List<Long> seconds = new ArrayList<>();
        for (PlaybookAction a : repository.findByIncident_Org_IdAndApprovedAtNotNull(actor.getOrgId())) {
            if (a.getIncident().getCreatedAt() != null && a.getApprovedAt() != null) {
                long s = Duration.between(a.getIncident().getCreatedAt(), a.getApprovedAt()).getSeconds();
                if (s >= 0) {
                    seconds.add(s);
                }
            }
        }
        if (seconds.isEmpty()) {
            return ApiResponse.ok(new ResponseTimeMetrics(0, 0, 0));
        }
        Collections.sort(seconds);
        double median = seconds.get(seconds.size() / 2);
        double avg = seconds.stream().mapToLong(Long::longValue).average().orElse(0);
        return ApiResponse.ok(new ResponseTimeMetrics(seconds.size(), median, Math.round(avg * 100.0) / 100.0));
    }
}
