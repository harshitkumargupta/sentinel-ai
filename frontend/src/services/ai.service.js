import api from './api.js';

// AI investigation + safe NL search. All endpoints require ANALYST+ and only do real work when the
// backend has AI enabled; otherwise analyses come back as deterministic FALLBACK and NL search
// returns a clear error.

export async function investigate(incidentId) {
  const { data } = await api.post(`/incidents/${incidentId}/investigate`);
  return data.data; // { analysisId, reused, state }
}

export async function listAnalyses(incidentId) {
  const { data } = await api.get(`/incidents/${incidentId}/analysis`);
  return data.data;
}

export async function getAnalysis(id) {
  const { data } = await api.get(`/analysis/${id}`);
  return data.data;
}

export async function reviewAnalysis(id, decision, note) {
  const { data } = await api.post(`/analysis/${id}/review`, { decision, note });
  return data.data;
}

export async function nlSearch(query) {
  const { data } = await api.post('/search/nl', { query });
  return data.data; // { interpretedFilter, results, total }
}

export async function getAiStatus() {
  const { data } = await api.get('/ai/status');
  return data.data; // { enabled, provider, offline, label, model }
}

export async function listAiQuestions() {
  const { data } = await api.get('/ai/questions');
  return data.data; // [{ intent, label }]
}

export async function askIncident(id, { intent, question }) {
  const { data } = await api.post(`/incidents/${id}/ask`, { intent, question });
  return data.data; // { intent, question, answer, bullets, evidenceEventIds, engine, offline }
}
