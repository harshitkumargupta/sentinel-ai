import api from './api.js';

// Demo Center (admin, demo/dev profile only) and the public demo flags used by the login page.

export async function getDemoInfo() {
  const { data } = await api.get('/public/demo');
  return data.data; // { demoMode, quickLogins: [{ username, password, role }] }
}

export async function listScenarios() {
  const { data } = await api.get('/demo/scenarios');
  return data.data;
}

export async function listChain() {
  const { data } = await api.get('/demo/chain');
  return data.data;
}

export async function runScenario(id) {
  const { data } = await api.post(`/demo/scenarios/${id}/run`);
  return data.data;
}

export async function seedDemo() {
  const { data } = await api.post('/demo/seed');
  return data.data;
}

export async function resetDemo() {
  const { data } = await api.post('/demo/reset');
  return data.data;
}

export async function listDatasets() {
  const { data } = await api.get('/demo/datasets');
  return data.data;
}

export async function replayDataset(id) {
  const { data } = await api.post(`/demo/replay/${id}`);
  return data.data;
}
