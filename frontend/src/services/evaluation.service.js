import api from './api.js';

export async function getEvaluation(runId) {
  const { data } = await api.get('/evaluation/detection', { params: { runId } });
  return data.data;
}
