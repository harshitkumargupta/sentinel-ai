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
    private final RuleConfigValidator validator;

    @Transactional(readOnly = true)
    public List<RuleResponse> list(AppUserPrincipal actor) {
        return ruleRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId()).stream().map(RuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse get(Long id, AppUserPrincipal actor) {
        return RuleResponse.from(load(id, actor));
    }

    public java.util.Set<String> ruleTypes() {
        return validator.ruleTypes();
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
                .config(validator.validate(actor.getOrgId(), req.ruleType(), req.config()))
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
        DetectionRule rule = load(id, actor);
        if (req.config() != null || req.ruleType() != null) {
            String type = req.ruleType() != null ? req.ruleType() : rule.getRuleType();
            String config = req.config() != null ? req.config() : rule.getConfig();
            rule.setConfig(validator.validate(actor.getOrgId(), type, config));
        }
        if (req.name() != null) {
            rule.setName(req.name());
        }
        if (req.description() != null) {
            rule.setDescription(req.description());
        }
        if (req.ruleType() != null) {
            rule.setRuleType(req.ruleType());
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
        DetectionRule rule = load(id, actor);
        rule.setEnabled(enabled);
        ruleRepository.save(rule);
        audit(actor, enabled ? "RULE_ENABLE" : "RULE_DISABLE", rule.getId());
        return RuleResponse.from(rule);
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        DetectionRule rule = load(id, actor);
        ruleRepository.delete(rule);
        audit(actor, "RULE_DELETE", id);
    }

    private DetectionRule load(Long id, AppUserPrincipal actor) {
        return ruleRepository.findById(id)
                .filter(r -> r.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Rule not found: " + id));
    }

    private void audit(AppUserPrincipal actor, String action, Long ruleId) {
        auditService.record(actor.getOrgId(), actor.getUserId(), action, "detection_rule", ruleId, null, null);
    }
}
