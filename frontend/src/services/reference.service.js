import api from './api.js';

export async function listReferenceSets() {
  const { data } = await api.get('/reference-sets');
  return data.data;
}

export async function createReferenceSet(body) {
  const { data } = await api.post('/reference-sets', body);
  return data.data;
}

export async function deleteReferenceSet(id) {
  await api.delete(`/reference-sets/${id}`);
}

export async function listReferenceItems(id, page = 0) {
  const { data } = await api.get(`/reference-sets/${id}/items`, { params: { page, size: 50 } });
  return data.data;
}

export async function addReferenceItems(id, values, note) {
  const { data } = await api.post(`/reference-sets/${id}/items`, { values, note });
  return data.data;
}

export async function removeReferenceItem(id, itemId) {
  await api.delete(`/reference-sets/${id}/items/${itemId}`);
}

export async function getThreatIntel() {
  const { data } = await api.get('/threat-intel');
  return data.data;
}

export async function checkThreatIntel(ip) {
  const { data } = await api.get('/threat-intel/check', { params: { ip } });
  return data.data;
}
