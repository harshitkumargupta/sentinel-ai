package com.sentinelai.evaluation.web;

import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.evaluation.EvaluationService;
import com.sentinelai.evaluation.dto.EvaluationResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@Tag(name = "Evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/detection")
    public ApiResponse<EvaluationResult> detection(@RequestParam String runId) {
        return ApiResponse.ok(evaluationService.evaluate(runId));
    }
}
