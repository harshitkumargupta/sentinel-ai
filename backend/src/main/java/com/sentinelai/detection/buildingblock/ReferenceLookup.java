package com.sentinelai.detection.buildingblock;

/**
 * Membership test for named reference sets (watchlists). Implemented by the reference-set module;
 * the default bean answers "not a member" so building blocks work before any set exists.
 */
public interface ReferenceLookup {

    boolean contains(Long orgId, String setName, String value);
}
