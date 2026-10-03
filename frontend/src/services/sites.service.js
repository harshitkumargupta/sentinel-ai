import api from './api.js';

export async function listSites() {
  const { data } = await api.get('/sites');
  return data.data;
}

export async function createSite(name, domain) {
  const { data } = await api.post('/sites', { name, domain });
  return data.data; // { site, apiKey: { apiKey, keyId, ... } }
}

export async function rotateKey(siteId) {
  const { data } = await api.post(`/sites/${siteId}/keys/rotate`);
  return data.data;
}

export async function revokeKey(keyId) {
  await api.delete(`/sites/keys/${keyId}`);
}

export async function getSnippet(siteId) {
  const { data } = await api.get(`/sites/${siteId}/snippet`);
  return data.data;
}
