package com.sentinelai.auth.web;

import com.sentinelai.auth.dto.LoginRequest;
import com.sentinelai.auth.dto.RefreshRequest;
import com.sentinelai.auth.dto.TokenResponse;
import com.sentinelai.auth.dto.UserResponse;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.auth.service.AuthService;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access + refresh tokens")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request.username(), request.password(),
                RequestUtils.clientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke all refresh tokens for the current user")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AppUserPrincipal principal,
                                    HttpServletRequest httpRequest) {
        authService.logout(principal, RequestUtils.clientIp(httpRequest));
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ApiResponse.ok(authService.me(principal));
    }
}
