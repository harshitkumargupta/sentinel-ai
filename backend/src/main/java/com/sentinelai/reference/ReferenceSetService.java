package com.sentinelai.reference;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.common.web.PageResponse;
import com.sentinelai.detection.buildingblock.BuildingBlockRepository;
import com.sentinelai.detection.buildingblock.Cidr;
import com.sentinelai.reference.web.ReferenceSetDtos.AddItemsResult;
import com.sentinelai.reference.web.ReferenceSetDtos.CreateSetRequest;
import com.sentinelai.reference.web.ReferenceSetDtos.ItemView;
import com.sentinelai.reference.web.ReferenceSetDtos.SetView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reference sets (watchlists): CRUD with per-type value validation, bulk add (deduplicated, capped),
 * delete protection while a building block uses the set, and an audit entry for every change.
 */
@Service
@RequiredArgsConstructor
public class ReferenceSetService {

    static final int MAX_ITEMS_PER_REQUEST = 5000;
    private static final Pattern USERNAME = Pattern.compile("^[\\p{L}\\p{N}._@\\\\\\-]{1,100}$");

    private final ReferenceSetRepository setRepository;
    private final ReferenceSetItemRepository itemRepository;
    private final BuildingBlockRepository buildingBlockRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SetView> list(AppUserPrincipal actor) {
        return setRepository.findByOrg_IdOrderByNameAsc(actor.getOrgId()).stream().map(this::view).toList();
    }

    @Transactional
    public SetView create(CreateSetRequest req, AppUserPrincipal actor) {
        String name = req.name().trim();
        if (name.regionMatches(true, 0, "TI:", 0, 3)) {
            throw new BadRequestException("Names starting with 'TI:' are reserved for threat-intel lists");
        }
        if (setRepository.existsByOrg_IdAndName(actor.getOrgId(), name)) {
            throw new ConflictException("A reference set with that name already exists");
        }
        ReferenceSet s = setRepository.save(ReferenceSet.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .name(name).elementType(req.elementType())
                .description(req.description() == null || req.description().isBlank() ? null : req.description().trim())
                .build());
        audit(actor, "REFERENCE_SET_CREATE", s.getId(), "{\"type\":\"" + s.getElementType() + "\"}");
        return view(s);
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        ReferenceSet s = load(id, actor);
        boolean used = buildingBlockRepository.findByOrg_IdOrderByNameAsc(actor.getOrgId()).stream()
                .anyMatch(b -> b.getConditions().contains("REFERENCE_SET") && b.getConditions().contains("\"" + s.getName() + "\""));
        if (used) {
            throw new ConflictException("Reference set is used by a building block");
        }
        setRepository.delete(s);
        audit(actor, "REFERENCE_SET_DELETE", id, "{}");
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemView> items(Long id, int page, int size, AppUserPrincipal actor) {
        load(id, actor);
        return PageResponse.from(itemRepository.findBySet_IdOrderByIdDesc(id,
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)))),
                i -> new ItemView(i.getId(), i.getValue(), i.getNote(),
                        i.getAddedBy() == null ? null : i.getAddedBy().getUsername(), i.getCreatedAt()));
    }

    @Transactional
    public AddItemsResult addItems(Long id, List<String> values, String note, AppUserPrincipal actor) {
        ReferenceSet s = load(id, actor);
        if (values.size() > MAX_ITEMS_PER_REQUEST) {
            throw new BadRequestException("Too many values (max " + MAX_ITEMS_PER_REQUEST + " per request)");
        }
        List<String> invalid = new ArrayList<>();
        int added = 0;
        int duplicates = 0;
        for (String raw : new LinkedHashSet<>(values)) {
            String v = raw == null ? "" : raw.strip();
            if (v.isEmpty()) {
                continue;
            }
            if (!valid(s.getElementType(), v)) {
                if (invalid.size() < 20) {
                    invalid.add(v.length() > 60 ? v.substring(0, 60) + "…" : v);
                }
                continue;
            }
            if (itemRepository.existsBySet_IdAndValue(id, v)) {
                duplicates++;
                continue;
            }
            itemRepository.save(ReferenceSetItem.builder().set(s).value(v)
                    .note(note == null || note.isBlank() ? null : note.strip())
                    .addedBy(userRepository.getReferenceById(actor.getUserId())).build());
            added++;
        }
        audit(actor, "REFERENCE_SET_ADD", id, "{\"added\":" + added + "}");
        return new AddItemsResult(added, duplicates, invalid);
    }

    @Transactional
    public void removeItem(Long id, Long itemId, AppUserPrincipal actor) {
        load(id, actor);
        ReferenceSetItem item = itemRepository.findById(itemId).filter(i -> i.getSet().getId().equals(id))
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
        itemRepository.delete(item);
        audit(actor, "REFERENCE_SET_REMOVE", id, "{\"itemId\":" + itemId + "}");
    }

    static boolean valid(ReferenceSetType type, String v) {
        return switch (type) {
            case IP -> v.contains("/") ? Cidr.isValid(v) : Cidr.isIpv4(v);
            case USERNAME -> USERNAME.matcher(v).matches();
            case TEXT -> v.length() <= 255 && v.chars().noneMatch(Character::isISOControl);
        };
    }

    private SetView view(ReferenceSet s) {
        return new SetView(s.getId(), s.getName(), s.getElementType(), s.getDescription(),
                itemRepository.countBySet_Id(s.getId()), s.getUpdatedAt());
    }

    private ReferenceSet load(Long id, AppUserPrincipal actor) {
        return setRepository.findById(id).filter(s -> s.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Reference set not found: " + id));
    }

    private void audit(AppUserPrincipal actor, String action, Long id, String details) {
        auditService.record(actor.getOrgId(), actor.getUserId(), action, "reference_set", id, details, null);
    }
}
