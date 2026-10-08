package com.sentinelai.threatintel;

import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.detection.buildingblock.Cidr;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Offline threat-intel lists: view (any role), check an IP (any role), reload from disk (admin). */
@RestController
@RequestMapping("/api/threat-intel")
@RequiredArgsConstructor
@Tag(name = "Threat intel")
public class ThreatIntelController {

    private final ThreatIntelService service;

    public record Status(Instant loadedAt, List<ThreatIntelService.Feed> feeds) {
    }

    @GetMapping
    public ApiResponse<Status> status() {
        return ApiResponse.ok(new Status(service.loadedAt(), service.feeds()));
    }

    @GetMapping("/check")
    public ApiResponse<ThreatIntelService.Lookup> check(@RequestParam String ip) {
        if (!Cidr.isIpv4(ip)) {
            throw new BadRequestException("Enter an IPv4 address");
        }
        return ApiResponse.ok(service.check(ip.trim()));
    }

    @PostMapping("/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Status> reload() {
        service.reload();
        return status();
    }
}
