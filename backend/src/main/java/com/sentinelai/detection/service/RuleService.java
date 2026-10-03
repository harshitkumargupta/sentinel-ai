package com.sentinelai.detection.service;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.dto.CreateRuleRequest;
import com.sentinelai.detection.dto.RuleResponse;
import com.sentinelai.detection.dto.UpdateRuleRequest;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final DetectionRuleRepository ruleRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        return ruleRepository.findAll().stream().map(RuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse get(Long id) {
        return RuleResponse.from(load(id));
    }

    @Transactional
    public RuleResponse create(CreateRuleRequest req, AppUserPrincipal actor) {
        if (ruleRepository.existsByName(req.name())) {
            throw new ConflictException("A rule with that name already exists");
        }
        DetectionRule rule = ruleRepository.save(DetectionRule.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .name(req.name())
                .description(req.description())
                .ruleType(req.ruleType())
                .config(req.config())
                .severity(req.severity())
                .mitreTechnique(req.mitreTechnique())
                .enabled(req.enabled() == null || req.enabled())
                .version(1)
                .createdBy(userRepository.getReferenceById(actor.getUserId()))
                .build());
        audit(actor, "RULE_CREATE", rule.getId());
        return RuleResponse.from(rule);
    }

    @Transactional
    public RuleResponse update(Long id, UpdateRuleRequest req, AppUserPrincipal actor) {
        DetectionRule rule = load(id);
        if (req.name() != null) {
            rule.setName(req.name());
        }
        if (req.description() != null) {
            rule.setDescription(req.description());
        }
        if (req.ruleType() != null) {
            rule.setRuleType(req.ruleType());
        }
        if (req.config() != null) {
            rule.setConfig(req.config());
        }
        if (req.severity() != null) {
            rule.setSeverity(req.severity());
        }
        if (req.mitreTechnique() != null) {
            rule.setMitreTechnique(req.mitreTechnique());
        }
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }
        rule.setVersion(rule.getVersion() + 1);
        ruleRepository.save(rule);
        audit(actor, "RULE_UPDATE", rule.getId());
        return RuleResponse.from(rule);
    }

    @Transactional
    public RuleResponse setEnabled(Long id, boolean enabled, AppUserPrincipal actor) {
        DetectionRule rule = load(id);
        rule.setEnabled(enabled);
        ruleRepository.save(rule);
        audit(actor, enabled ? "RULE_ENABLE" : "RULE_DISABLE", rule.getId());
        return RuleResponse.from(rule);
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        DetectionRule rule = load(id);
        ruleRepository.delete(rule);
        audit(actor, "RULE_DELETE", id);
    }

    private DetectionRule load(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rule not found: " + id));
    }

    private void audit(AppUserPrincipal actor, String action, Long ruleId) {
        auditService.record(actor.getOrgId(), actor.getUserId(), action, "detection_rule", ruleId, null, null);
    }
}
