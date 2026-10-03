package com.sentinelai.auth.web;

import com.sentinelai.auth.dto.CreateUserRequest;
import com.sentinelai.auth.dto.UpdateUserRequest;
import com.sentinelai.auth.dto.UserResponse;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.auth.service.UserService;
import com.sentinelai.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users (admin)")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok(userService.list());
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(userService.create(request, actor));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateUserRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(userService.update(id, request, actor));
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<UserResponse> disable(@PathVariable Long id,
                                             @AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(userService.disable(id, actor));
    }
}
