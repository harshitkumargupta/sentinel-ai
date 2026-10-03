import api from './api.js';

export async function getSummary() {
  const { data } = await api.get('/dashboard/summary');
  return data.data;
}
