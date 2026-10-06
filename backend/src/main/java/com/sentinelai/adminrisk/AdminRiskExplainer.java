package com.sentinelai.adminrisk;

/**
 * Produces a human-readable explanation of an admin-risk decision. A template implementation ships
 * now; an AI-backed implementation can replace it later without changing callers (AI is not wired).
 */
public interface AdminRiskExplainer {

    String explain(AdminActionContext ctx, AdminRiskResult result, String decision);
}
