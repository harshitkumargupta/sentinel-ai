package com.sentinelai.reference;

import com.sentinelai.detection.buildingblock.Cidr;
import com.sentinelai.detection.buildingblock.ReferenceLookup;
import com.sentinelai.threatintel.ThreatIntelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ReferenceLookup} for building blocks: {@code TI:<list>} names test the offline threat-intel
 * lists; any other name is a reference set of the org (exact value, or CIDR containment for IP sets).
 */
@Component
@RequiredArgsConstructor
public class DbReferenceLookup implements ReferenceLookup {

    private final ReferenceSetRepository setRepository;
    private final ReferenceSetItemRepository itemRepository;
    private final ThreatIntelService threatIntel;

    @Override
    @Transactional(readOnly = true)
    public boolean contains(Long orgId, String setName, String value) {
        if (setName.regionMatches(true, 0, "TI:", 0, 3)) {
            return threatIntel.contains(setName.substring(3).strip(), value);
        }
        return setRepository.findByOrg_IdAndName(orgId, setName).map(s -> {
            if (itemRepository.existsBySet_IdAndValue(s.getId(), value)) {
                return true;
            }
            return s.getElementType() == ReferenceSetType.IP
                    && itemRepository.cidrValues(s.getId()).stream().anyMatch(c -> Cidr.contains(c, value));
        }).orElse(false);
    }
}
