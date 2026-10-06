package com.sentinelai.kafka.admin;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.common.web.RequestUtils;
import com.sentinelai.kafka.admin.KafkaAdminDtos.ChaosBurstResult;
import com.sentinelai.kafka.admin.KafkaAdminDtos.DlqMessageResponse;
import com.sentinelai.kafka.admin.KafkaAdminDtos.PipelineStatus;
import com.sentinelai.kafka.admin.KafkaAdminDtos.ReplayResult;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.dlq.DlqMessage;
import com.sentinelai.kafka.dlq.DlqMessageRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for the Kafka pipeline: DLQ inspection/replay, chaos controls and pipeline status. Only
 * registered when the pipeline is enabled; otherwise these endpoints are absent (the UI degrades).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Kafka pipeline")
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class KafkaPipelineController {

    private final DlqMessageRepository dlqRepository;
    private final DlqAdminService dlqAdminService;
    private final ChaosService chaosService;
    private final PipelineStatusService statusService;
    private final KafkaProperties props;

    public KafkaPipelineController(DlqMessageRepository dlqRepository, DlqAdminService dlqAdminService,
                                   ChaosService chaosService, PipelineStatusService statusService,
                                   KafkaProperties props) {
        this.dlqRepository = dlqRepository;
        this.dlqAdminService = dlqAdminService;
        this.chaosService = chaosService;
        this.statusService = statusService;
        this.props = props;
    }

    @GetMapping("/dlq")
    public ApiResponse<PageResponse<DlqMessageResponse>> dlq(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = dlqRepository.findByStatusOrderByIdDesc(
                DlqMessage.Status.DEAD, PageRequest.of(page, Math.min(size, 200)));
        return ApiResponse.ok(PageResponse.from(result, DlqMessageResponse::from));
    }

    @PostMapping("/dlq/{id}/replay")
    public ApiResponse<Void> replay(@PathVariable Long id,
                                    @AuthenticationPrincipal AppUserPrincipal actor,
                                    HttpServletRequest request) {
        dlqAdminService.replay(id, actor.getOrgId(), actor.getUserId(), RequestUtils.clientIp(request));
        return ApiResponse.ok(null);
    }

    @PostMapping("/dlq/replay-all")
    public ApiResponse<ReplayResult> replayAll(@AuthenticationPrincipal AppUserPrincipal actor,
                                               HttpServletRequest request) {
        int n = dlqAdminService.replayAll(actor.getOrgId(), actor.getUserId(), RequestUtils.clientIp(request));
        return ApiResponse.ok(new ReplayResult(n));
    }

    @GetMapping("/pipeline-status")
    public ApiResponse<PipelineStatus> status() {
        return ApiResponse.ok(statusService.status());
    }

    @PostMapping("/chaos/pause")
    public ApiResponse<Void> pause(@RequestParam String listener,
                                   @AuthenticationPrincipal AppUserPrincipal actor,
                                   HttpServletRequest request) {
        chaosService.pause(listener, actor.getOrgId(), actor.getUserId(), RequestUtils.clientIp(request));
        return ApiResponse.ok(null);
    }

    @PostMapping("/chaos/resume")
    public ApiResponse<Void> resume(@RequestParam String listener,
                                    @AuthenticationPrincipal AppUserPrincipal actor,
                                    HttpServletRequest request) {
        chaosService.resume(listener, actor.getOrgId(), actor.getUserId(), RequestUtils.clientIp(request));
        return ApiResponse.ok(null);
    }

    @PostMapping("/chaos/burst")
    public ApiResponse<ChaosBurstResult> burst(@RequestParam(defaultValue = "1000") int count,
                                               @AuthenticationPrincipal AppUserPrincipal actor) {
        int published = chaosService.burst(count, actor.getOrgId());
        return ApiResponse.ok(new ChaosBurstResult(published, props.getTopics().getRaw()));
    }
}
