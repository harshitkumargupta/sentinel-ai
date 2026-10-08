import api from './api.js';

export async function listIncidents(params = {}) {
  const { data } = await api.get('/incidents', { params });
  return data.data; // PageResponse
}

export async function getIncident(id) {
  const { data } = await api.get(`/incidents/${id}`);
  return data.data;
}

export async function getTimeline(id) {
  const { data } = await api.get(`/incidents/${id}/timeline`);
  return data.data;
}

export async function getRisk(id) {
  const { data } = await api.get(`/incidents/${id}/risk`);
  return data.data;
}

export async function getEvidence(id) {
  const { data } = await api.get(`/incidents/${id}/evidence`);
  return data.data;
}

export async function getGraph(id) {
  const { data } = await api.get(`/incidents/${id}/graph`);
  return data.data;
}

export async function updateStatus(id, status) {
  const { data } = await api.patch(`/incidents/${id}/status`, { status });
  return data.data;
}

export async function assignIncident(id, assigneeId) {
  const { data } = await api.patch(`/incidents/${id}/assign`, { assigneeId });
  return data.data;
}

export async function setFeedback(id, feedback) {
  const { data } = await api.patch(`/incidents/${id}/feedback`, { feedback });
  return data.data;
}

export async function getSimilar(id, limit = 5) {
  const { data } = await api.get(`/incidents/${id}/similar`, { params: { limit } });
  return data.data;
}

export async function setPriority(id, priority) {
  const { data } = await api.patch(`/incidents/${id}/priority`, { priority });
  return data.data;
}

export async function getCaseTimeline(id) {
  const { data } = await api.get(`/incidents/${id}/case-timeline`);
  return data.data; // [{ at, kind, actor, title, detail }]
}
