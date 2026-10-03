import api from './api.js';

export async function listAlerts(params = {}) {
  const { data } = await api.get('/alerts', { params });
  return data.data;
}
