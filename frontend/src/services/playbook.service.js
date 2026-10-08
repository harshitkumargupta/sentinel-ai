import api from './api.js';

// SOAR playbook actions on an incident. All require ANALYST+; HIGH/CRITICAL approval is further
// restricted server-side (ADMIN, proposer≠approver, admin-risk guard).

export async function listActions(incidentId) {
  const { data } = await api.get(`/incidents/${incidentId}/actions`);
  return data.data;
}

export async function dryRunAction(id) {
  const { data } = await api.post(`/actions/${id}/dry-run`);
  return data.data;
}

export async function approveAction(id, stepUp = false) {
  const { data } = await api.post(`/actions/${id}/approve`, null, { params: { stepUp } });
  return data.data;
}

export async function rejectAction(id) {
  const { data } = await api.post(`/actions/${id}/reject`);
  return data.data;
}

export async function executeAction(id, confirm = false) {
  const { data } = await api.post(`/actions/${id}/execute`, null, { params: { confirm } });
  return data.data;
}

export async function rollbackAction(id) {
  const { data } = await api.post(`/actions/${id}/rollback`);
  return data.data;
}

export async function listActionTargets(incidentId) {
  const { data } = await api.get(`/incidents/${incidentId}/action-targets`);
  return data.data; // { ips, users, hosts }
}

export async function proposeAction(incidentId, actionType, target, reason) {
  const { data } = await api.post(`/incidents/${incidentId}/actions`, { actionType, target, reason });
  return data.data;
}
