import api from './api.js';

export async function listPending() {
  const { data } = await api.get('/admin/pending');
  return data.data;
}

export async function approvePending(id) {
  const { data } = await api.post(`/admin/pending/${id}/approve`);
  return data.data;
}

export async function rejectPending(id) {
  const { data } = await api.post(`/admin/pending/${id}/reject`);
  return data.data;
}

export async function adminTimeline(params = {}) {
  const { data } = await api.get('/admin/timeline', { params });
  return data.data;
}

export async function listSessions(userId) {
  const { data } = await api.get(`/admin/sessions/${userId}`);
  return data.data;
}

export async function revokeSessions(userId) {
  const { data } = await api.post(`/admin/sessions/${userId}/revoke`);
  return data.data;
}

export async function guardedDisableUser(userId, stepUp = false) {
  const { data } = await api.post(`/admin/actions/disable-user/${userId}`, null, { params: { stepUp } });
  return data.data; // GuardDecision
}
