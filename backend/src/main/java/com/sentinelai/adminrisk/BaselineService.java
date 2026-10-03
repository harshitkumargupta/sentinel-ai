package com.sentinelai.adminrisk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.adminrisk.domain.AdminBaseline;
import com.sentinelai.adminrisk.domain.AdminBaselineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads an admin's baseline sets from {@code admin_baselines}. */
@Service
@RequiredArgsConstructor
public class BaselineService {

    private final AdminBaselineRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminBaselines load(Long userId) {
        Map<String, String> byMetric = new HashMap<>();
        for (AdminBaseline b : repository.findById_UserId(userId)) {
            byMetric.put(b.getId().getMetric(), b.getData());
        }
        return new AdminBaselines(
                strings(byMetric.get("known_countries")),
                strings(byMetric.get("known_ips")),
                strings(byMetric.get("known_devices")),
                ints(byMetric.get("typical_hours")),
                longs(byMetric.get("known_sites")));
    }

    private Set<String> strings(String json) {
        return parse(json, String[].class);
    }

    private Set<Integer> ints(String json) {
        return parse(json, Integer[].class);
    }

    private Set<Long> longs(String json) {
        return parse(json, Long[].class);
    }

    private <T> Set<T> parse(String json, Class<T[]> type) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return java.util.Arrays.stream(objectMapper.readValue(json, type)).collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }
}
