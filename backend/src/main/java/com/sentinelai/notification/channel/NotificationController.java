package com.sentinelai.notification.channel;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.notification.domain.Notification;
import com.sentinelai.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** In-app bell (any user, own notifications) and notification settings (admin). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationSettingsService settings;
    private final NotificationRepository notifications;

    public record ChannelRequest(@NotBlank @Size(max = 100) String name, @NotNull ChannelType type,
                                 @Size(max = 500) String target, Boolean enabled) {
    }

    public record RuleRequest(@NotBlank @Size(max = 100) String name, @NotNull Severity minSeverity,
                              @Size(max = 50) String ruleType, boolean onIncidentCreated, boolean onEscalation,
                              @NotNull @Size(min = 1, max = 20) List<Long> channelIds, Boolean enabled) {
    }

    public record BellItem(Long id, String message, Long incidentId, boolean read, Instant createdAt) {
    }

    @GetMapping("/api/notifications")
    @Transactional(readOnly = true)
    public ApiResponse<List<BellItem>> mine(@AuthenticationPrincipal AppUserPrincipal actor) {
        return ApiResponse.ok(notifications.findTop50ByUser_IdOrderByIdDesc(actor.getUserId()).stream()
                .map(n -> new BellItem(n.getId(), n.getMessage(), n.getIncidentId(), n.isRead(), n.getCreatedAt())).toList());
    }

    @PostMapping("/api/notifications/read-all")
    @Transactional
    public ApiResponse<Integer> readAll(@AuthenticationPrincipal AppUserPrincipal actor) {
        List<Notification> unread = notifications.findTop50ByUser_IdOrderByIdDesc(actor.getUserId()).stream()
                .filter(n -> !n.isRead()).toList();
        unread.forEach(n -> n.setRead(true));
        notifications.saveAll(unread);
        return ApiResponse.ok(unread.size());
    }

    @GetMapping("/api/notification-settings/channels")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<NotificationSettingsService.ChannelView>> channels(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.channels(a.getOrgId()));
    }

    @PostMapping("/api/notification-settings/channels")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationSettingsService.ChannelView> createChannel(@Valid @RequestBody ChannelRequest r,
                                                                             @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.saveChannel(a.getOrgId(), a.getUserId(), null, r.name(), r.type(), r.target(),
                r.enabled() == null || r.enabled()));
    }

    @PutMapping("/api/notification-settings/channels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationSettingsService.ChannelView> updateChannel(@PathVariable Long id, @Valid @RequestBody ChannelRequest r,
                                                                             @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.saveChannel(a.getOrgId(), a.getUserId(), id, r.name(), r.type(), r.target(),
                r.enabled() == null || r.enabled()));
    }

    @DeleteMapping("/api/notification-settings/channels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteChannel(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal a) {
        settings.deleteChannel(a.getOrgId(), a.getUserId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/notification-settings/channels/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationDelivery> test(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.test(a.getOrgId(), id));
    }

    @GetMapping("/api/notification-settings/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<NotificationSettingsService.RuleView>> rules(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.rules(a.getOrgId()));
    }

    @PostMapping("/api/notification-settings/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationSettingsService.RuleView> createRule(@Valid @RequestBody RuleRequest r,
                                                                       @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.saveRule(a.getOrgId(), a.getUserId(), null, r.name(), r.minSeverity(), r.ruleType(),
                r.onIncidentCreated(), r.onEscalation(), r.channelIds(), r.enabled() == null || r.enabled()));
    }

    @PutMapping("/api/notification-settings/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationSettingsService.RuleView> updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest r,
                                                                       @AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.saveRule(a.getOrgId(), a.getUserId(), id, r.name(), r.minSeverity(), r.ruleType(),
                r.onIncidentCreated(), r.onEscalation(), r.channelIds(), r.enabled() == null || r.enabled()));
    }

    @DeleteMapping("/api/notification-settings/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal a) {
        settings.deleteRule(a.getOrgId(), a.getUserId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/notification-settings/deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<NotificationDelivery>> deliveries(@AuthenticationPrincipal AppUserPrincipal a) {
        return ApiResponse.ok(settings.deliveries(a.getOrgId()));
    }
}
