package com.sentinelai.detection.buildingblock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.buildingblock.BuildingBlockDtos.BuildingBlockView;
import com.sentinelai.detection.buildingblock.BuildingBlockDtos.ConditionDto;
import com.sentinelai.detection.buildingblock.BuildingBlockDtos.SaveBuildingBlockRequest;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Building-block CRUD with validation (known fields, valid CIDRs/numbers per operator), rename-safe
 * references (renaming a block in use is refused), delete protection while rules reference it, and
 * matcher cache eviction on every change. All changes are audited.
 */
@Service
@RequiredArgsConstructor
public class BuildingBlockService {

    private final BuildingBlockRepository repository;
    private final DetectionRuleRepository ruleRepository;
    private final OrganizationRepository organizationRepository;
    private final BuildingBlockMatcher matcher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<BuildingBlockView> list(AppUserPrincipal actor) {
        List<DetectionRule> rules = ruleRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId());
        return repository.findByOrg_IdOrderByNameAsc(actor.getOrgId()).stream()
                .map(b -> view(b, rules)).toList();
    }

    @Transactional
    public BuildingBlockView create(SaveBuildingBlockRequest req, AppUserPrincipal actor) {
        String name = req.name().trim();
        if (repository.existsByOrg_IdAndName(actor.getOrgId(), name)) {
            throw new ConflictException("A building block with that name already exists");
        }
        BuildingBlock b = repository.save(BuildingBlock.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .name(name).description(trim(req.description()))
                .conditions(toJson(validate(req.conditions())))
                .build());
        changed(actor, "BUILDING_BLOCK_CREATE", b);
        return view(b, ruleRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId()));
    }

    @Transactional
    public BuildingBlockView update(Long id, SaveBuildingBlockRequest req, AppUserPrincipal actor) {
        BuildingBlock b = load(id, actor);
        List<DetectionRule> rules = ruleRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId());
        String name = req.name().trim();
        if (!name.equals(b.getName())) {
            if (!usedBy(b.getName(), rules).isEmpty()) {
                throw new ConflictException("Rename refused: rules reference this building block by name");
            }
            if (repository.existsByOrg_IdAndName(actor.getOrgId(), name)) {
                throw new ConflictException("A building block with that name already exists");
            }
        }
        b.setName(name);
        b.setDescription(trim(req.description()));
        b.setConditions(toJson(validate(req.conditions())));
        repository.save(b);
        changed(actor, "BUILDING_BLOCK_UPDATE", b);
        return view(b, rules);
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        BuildingBlock b = load(id, actor);
        List<String> users = usedBy(b.getName(), ruleRepository.findByOrg_IdOrderByIdAsc(actor.getOrgId()));
        if (!users.isEmpty()) {
            throw new ConflictException("Building block is used by rule(s): " + String.join(", ", users));
        }
        repository.delete(b);
        changed(actor, "BUILDING_BLOCK_DELETE", b);
    }

    /** Names that exist for this org (rule-config validation). */
    @Transactional(readOnly = true)
    public boolean exists(Long orgId, String name) {
        return repository.existsByOrg_IdAndName(orgId, name);
    }

    private List<Condition> validate(List<ConditionDto> dtos) {
        return dtos.stream().map(d -> {
            ConditionField field;
            try {
                field = ConditionField.parse(d.field());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown condition field: " + d.field());
            }
            List<String> values = d.values().stream().map(String::trim).toList();
            boolean needsValues = d.op() != ConditionOperator.NOT_EQUALS && d.op() != ConditionOperator.NOT_IN;
            if (needsValues && values.isEmpty()) {
                throw new BadRequestException("Operator " + d.op() + " needs at least one value");
            }
            if ((d.op() == ConditionOperator.IN_CIDR || d.op() == ConditionOperator.NOT_IN_CIDR)
                    && values.stream().anyMatch(v -> !Cidr.isValid(v))) {
                throw new BadRequestException("Invalid CIDR in condition on " + d.field());
            }
            if (field == ConditionField.HOUR && values.stream().anyMatch(v -> !v.matches("^([01]?\\d|2[0-3])$"))) {
                throw new BadRequestException("Hour values must be 0-23");
            }
            return new Condition(d.field(), d.op(), values);
        }).toList();
    }

    private List<String> usedBy(String blockName, List<DetectionRule> rules) {
        return rules.stream().filter(r -> matcher.referencedBy(r.getConfig()).contains(blockName))
                .map(DetectionRule::getName).toList();
    }

    private BuildingBlockView view(BuildingBlock b, List<DetectionRule> rules) {
        return new BuildingBlockView(b.getId(), b.getName(), b.getDescription(), matcher.parse(b.getConditions()),
                usedBy(b.getName(), rules), b.getUpdatedAt());
    }

    private BuildingBlock load(Long id, AppUserPrincipal actor) {
        return repository.findById(id).filter(b -> b.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Building block not found: " + id));
    }

    private void changed(AppUserPrincipal actor, String action, BuildingBlock b) {
        matcher.evict();
        auditService.record(actor.getOrgId(), actor.getUserId(), action, "building_block", b.getId(),
                "{\"name\":\"" + b.getName().replace("\"", "'") + "\"}", null);
    }

    private String toJson(List<Condition> conditions) {
        try {
            return objectMapper.writeValueAsString(conditions);
        } catch (Exception e) {
            throw new BadRequestException("Could not serialize conditions");
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
