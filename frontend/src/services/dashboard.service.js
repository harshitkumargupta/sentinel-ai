import api from './api.js';

export async function getSummary() {
  const { data } = await api.get('/dashboard/summary');
  return data.data;
}

export async function getAlertReduction() {
  const { data } = await api.get('/dashboard/alert-reduction');
  return data.data;
}

export async function getMitreCoverage() {
  const { data } = await api.get('/dashboard/mitre-coverage');
  return data.data;
}

export async function getGeoFlows() {
  const { data } = await api.get('/dashboard/geo-flows');
  return data.data;
}
