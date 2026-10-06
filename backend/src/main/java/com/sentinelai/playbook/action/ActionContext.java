package com.sentinelai.playbook.action;

/** Inputs an action needs: the org and the target (an IP or username). */
public record ActionContext(Long orgId, String target) {
}
