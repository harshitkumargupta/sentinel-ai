import api from './api.js';

// Kafka pipeline admin API. All endpoints require ADMIN and only exist when the pipeline is
// enabled on the backend (otherwise they return 404, which the page treats as "disabled").

export async function getPipelineStatus() {
  const { data } = await api.get('/admin/pipeline-status');
  return data.data;
}

export async function listDlq(params = {}) {
  const { data } = await api.get('/admin/dlq', { params });
  return data.data;
}

export async function replayDlq(id) {
  const { data } = await api.post(`/admin/dlq/${id}/replay`);
  return data.data;
}

export async function replayAllDlq() {
  const { data } = await api.post('/admin/dlq/replay-all');
  return data.data;
}

export async function pauseConsumer(listener) {
  const { data } = await api.post('/admin/chaos/pause', null, { params: { listener } });
  return data.data;
}

export async function resumeConsumer(listener) {
  const { data } = await api.post('/admin/chaos/resume', null, { params: { listener } });
  return data.data;
}

export async function publishBurst(count) {
  const { data } = await api.post('/admin/chaos/burst', null, { params: { count } });
  return data.data;
}
