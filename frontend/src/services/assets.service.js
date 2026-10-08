import api from './api.js';

export const ASSET_TYPES = ['SERVER', 'WORKSTATION', 'LAPTOP', 'NETWORK', 'CLOUD', 'APPLICATION', 'OTHER'];
export const ENVIRONMENTS = ['PRODUCTION', 'STAGING', 'DEVELOPMENT', 'CORPORATE'];
export const CRITICALITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export async function listAssets() {
  const { data } = await api.get('/assets');
  return data.data;
}

export async function getAsset(id) {
  const { data } = await api.get(`/assets/${id}`);
  return data.data; // { asset, incidents }
}

export async function saveAsset(id, body) {
  const { data } = id ? await api.put(`/assets/${id}`, body) : await api.post('/assets', body);
  return data.data;
}

export async function deleteAsset(id) {
  await api.delete(`/assets/${id}`);
}

export async function importAssets(file) {
  const form = new FormData();
  form.append('file', file);
  const { data } = await api.post('/assets/import', form, { headers: { 'Content-Type': 'multipart/form-data' } });
  return data.data;
}

export async function relinkAssets() {
  const { data } = await api.post('/assets/relink');
  return data.data;
}

export async function listVulnerabilities(assetId) {
  const { data } = await api.get('/vulnerabilities', { params: assetId ? { assetId } : {} });
  return data.data;
}

export async function importVulnerabilities(file) {
  const form = new FormData();
  form.append('file', file);
  const { data } = await api.post('/vulnerabilities/import', form, { headers: { 'Content-Type': 'multipart/form-data' } });
  return data.data;
}

export async function setVulnerabilityStatus(id, status) {
  const { data } = await api.patch(`/vulnerabilities/${id}/status`, { status });
  return data.data;
}
