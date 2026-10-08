import api from './api.js';

// Offenses (QRadar naming) over incidents: magnitude-ranked list, detail, notes, assignees.

export async function listOffenses(params = {}) {
  const { data } = await api.get('/offenses', { params });
  return data.data; // { content, page, size, totalElements, totalPages }
}

export async function getOffense(id) {
  const { data } = await api.get(`/offenses/${id}`);
  return data.data; // { offense, threatIntel, events, notes }
}

export async function listAssignees() {
  const { data } = await api.get('/offenses/assignees');
  return data.data;
}

export async function addNote(id, body) {
  const { data } = await api.post(`/offenses/${id}/notes`, { body });
  return data.data;
}
