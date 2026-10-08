package com.sentinelai.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/** Channel/rule CRUD with target validation, test sends, and the delivery log. Admin-only via controller. */
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[A-Za-z]{2,}$");

    private final NotificationChannelRepository channels;
    private final NotificationRuleRepository rules;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationDispatcher dispatcher;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public record ChannelView(Long id, String name, ChannelType type, String target, boolean enabled, boolean mock) {
    }

    public record RuleView(Long id, String name, Severity minSeverity, String ruleType, boolean onIncidentCreated,
                           boolean onEscalation, List<Long> channelIds, boolean enabled) {
    }

    @Transactional(readOnly = true)
    public List<ChannelView> channels(Long orgId) {
        return channels.findByOrgIdOrderByIdAsc(orgId).stream().map(this::view).toList();
    }

    @Transactional
    public ChannelView saveChannel(Long orgId, Long userId, Long id, String name, ChannelType type, String target, boolean enabled) {
        String t = target == null || target.isBlank() ? null : target.trim();
        if (type == ChannelType.EMAIL && (t == null || !EMAIL.matcher(t).matches())) {
            throw new BadRequestException("Email channels need a valid email address");
        }
        if (type == ChannelType.WEBHOOK) {
            validateWebhook(t);
        }
        NotificationChannel c = id == null ? new NotificationChannel() : loadChannel(orgId, id);
        c.setOrgId(orgId);
        c.setName(name.trim());
        c.setType(type);
        c.setTarget(type == ChannelType.IN_APP ? null : t);
        c.setEnabled(enabled);
        NotificationChannel saved = channels.save(c);
        auditService.record(orgId, userId, "NOTIFY_CHANNEL_SAVE", "notification_channel", saved.getId(), "{\"type\":\"" + type + "\"}", null);
        return view(saved);
    }

    @Transactional
    public void deleteChannel(Long orgId, Long userId, Long id) {
        channels.delete(loadChannel(orgId, id));
        auditService.record(orgId, userId, "NOTIFY_CHANNEL_DELETE", "notification_channel", id, "{}", null);
    }

    /** Send a test message now (with the normal retries) and return the recorded delivery. */
    @Transactional
    public NotificationDelivery test(Long orgId, Long id) {
        NotificationChannel c = loadChannel(orgId, id);
        return dispatcher.deliver(c, null, new ChannelSender.Message(orgId, null,
                "[SentinelAI] Test notification for channel '" + c.getName() + "'",
                "If you can read this, the channel works."), true);
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules(Long orgId) {
        return rules.findByOrgIdOrderByIdAsc(orgId).stream().map(this::view).toList();
    }

    @Transactional
    public RuleView saveRule(Long orgId, Long userId, Long id, String name, Severity minSeverity, String ruleType,
                             boolean created, boolean escalation, List<Long> channelIds, boolean enabled) {
        if (channelIds == null || channelIds.isEmpty()) {
            throw new BadRequestException("Pick at least one channel");
        }
        channelIds.forEach(cid -> loadChannel(orgId, cid));
        NotificationRule r = id == null ? new NotificationRule() : loadRule(orgId, id);
        r.setOrgId(orgId);
        r.setName(name.trim());
        r.setMinSeverity(minSeverity);
        r.setRuleType(ruleType == null || ruleType.isBlank() ? null : ruleType.trim());
        r.setOnIncidentCreated(created);
        r.setOnEscalation(escalation);
        try {
            r.setChannelIds(objectMapper.writeValueAsString(channelIds));
        } catch (Exception e) {
            throw new BadRequestException("Invalid channels");
        }
        r.setEnabled(enabled);
        NotificationRule saved = rules.save(r);
        auditService.record(orgId, userId, "NOTIFY_RULE_SAVE", "notification_rule", saved.getId(), "{}", null);
        return view(saved);
    }

    @Transactional
    public void deleteRule(Long orgId, Long userId, Long id) {
        rules.delete(loadRule(orgId, id));
        auditService.record(orgId, userId, "NOTIFY_RULE_DELETE", "notification_rule", id, "{}", null);
    }

    @Transactional(readOnly = true)
    public List<NotificationDelivery> deliveries(Long orgId) {
        return deliveries.findByOrgIdOrderByIdDesc(orgId, PageRequest.of(0, 100));
    }

    /** http(s) only; no credentials in the URL; never the cloud metadata address. */
    static void validateWebhook(String url) {
        if (url == null) {
            throw new BadRequestException("Webhook channels need a URL");
        }
        URI u;
        try {
            u = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid webhook URL");
        }
        if (!"http".equalsIgnoreCase(u.getScheme()) && !"https".equalsIgnoreCase(u.getScheme())) {
            throw new BadRequestException("Webhook URL must be http or https");
        }
        if (u.getHost() == null || u.getUserInfo() != null) {
            throw new BadRequestException("Webhook URL needs a host and no credentials");
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
                if (a.isLinkLocalAddress() || a.getHostAddress().startsWith("169.254.")) {
                    throw new BadRequestException("Webhook URL may not target link-local / metadata addresses");
                }
            }
        } catch (java.net.UnknownHostException e) {
            // offline demo: an unresolvable host is allowed to be saved; delivery will fail and be recorded
        }
    }

    private ChannelView view(NotificationChannel c) {
        boolean mock = c.getType() == ChannelType.EMAIL && !emailSender.configured();
        return new ChannelView(c.getId(), c.getName(), c.getType(), c.getTarget(), c.isEnabled(), mock);
    }

    private RuleView view(NotificationRule r) {
        List<Long> ids;
        try {
            ids = objectMapper.readValue(r.getChannelIds(), new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
        } catch (Exception e) {
            ids = List.of();
        }
        return new RuleView(r.getId(), r.getName(), r.getMinSeverity(), r.getRuleType(), r.isOnIncidentCreated(),
                r.isOnEscalation(), ids, r.isEnabled());
    }

    private NotificationChannel loadChannel(Long orgId, Long id) {
        return channels.findById(id).filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Channel not found: " + id));
    }

    private NotificationRule loadRule(Long orgId, Long id) {
        return rules.findById(id).filter(r -> r.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Rule not found: " + id));
    }
}
