import api from './api.js';

export async function runSimulator(body) {
  const { data } = await api.post('/simulator/run', body);
  return data.data;
}

export async function listRuns() {
  const { data } = await api.get('/simulator/runs');
  return data.data;
}
