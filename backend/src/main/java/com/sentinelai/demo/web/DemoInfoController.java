package com.sentinelai.demo.web;

import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.demo.DemoProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (pre-login) demo flags for the login page. The seeded demo accounts are listed only when
 * {@code sentinel.demo.quick-login} is on — i.e. the demo profile, whose accounts are documented
 * and synthetic. In every other profile this returns {@code demoMode=false} and no accounts.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Demo Center")
public class DemoInfoController {

    private final DemoProperties properties;

    public record QuickLogin(String username, String password, String role) {
    }

    public record DemoInfo(boolean demoMode, List<QuickLogin> quickLogins) {
    }

    /** Must match the accounts seeded by {@code DevDataSeeder} (documented in the README). */
    static final List<QuickLogin> ACCOUNTS = List.of(
            new QuickLogin("admin", "Admin@123", "ADMIN"),
            new QuickLogin("analyst", "Analyst@123", "ANALYST"),
            new QuickLogin("viewer", "Viewer@123", "VIEWER"));

    @GetMapping("/api/public/demo")
    public ApiResponse<DemoInfo> info() {
        boolean quick = properties.isEnabled() && properties.isQuickLogin();
        return ApiResponse.ok(new DemoInfo(properties.isEnabled(), quick ? ACCOUNTS : List.of()));
    }
}
