package com.sentinelai.ml.web;

import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.ml.MlFeatureExtractor;
import com.sentinelai.simulator.domain.SimLabel;
import com.sentinelai.simulator.repository.SimLabelRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** Exports labeled feature rows (from a simulator run's sim_labels) for ml/train.py --source. */
@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ML export")
public class MlExportController {

    private final SimLabelRepository simLabelRepository;
    private final SecurityEventRepository eventRepository;
    private final MlFeatureExtractor extractor;

    public record Row(List<Double> features, int label, int ruleFlagged, String scenario) {
    }

    public record TrainingData(List<String> featureNames, List<Row> rows) {
    }

    @GetMapping("/training-data")
    public ApiResponse<TrainingData> trainingData(@RequestParam String runId) {
        List<Row> rows = new ArrayList<>();
        for (SimLabel label : simLabelRepository.findByRunId(runId)) {
            eventRepository.findById(label.getEventId()).ifPresent(event ->
                    rows.add(new Row(extractor.singleEventFeatures(event),
                            label.isAttack() ? 1 : 0, 0, label.getScenarioId())));
        }
        return ApiResponse.ok(new TrainingData(MlFeatureExtractor.FEATURE_NAMES, rows));
    }
}
