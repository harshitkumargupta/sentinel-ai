import api from './api.js';

export const SOURCE_TYPES = [
  { value: 'WEB_SERVER', label: 'Web server (nginx / Apache access log)' },
  { value: 'AUTH', label: 'Authentication (Linux auth.log)' },
  { value: 'FIREWALL', label: 'Firewall' },
  { value: 'APPLICATION', label: 'Application (JSON)' },
  { value: 'GENERIC', label: 'Generic JSON' },
];

export async function listLogSources() {
  const { data } = await api.get('/log-sources');
  return data.data;
}

export async function createLogSource(body) {
  const { data } = await api.post('/log-sources', body);
  return data.data; // { source, apiKey: { apiKey, keyId, ... } } — the raw key is shown once
}

export async function setLogSourceEnabled(id, enabled) {
  const { data } = await api.patch(`/log-sources/${id}/enabled`, { enabled });
  return data.data;
}

export async function rotateLogSourceKey(id) {
  const { data } = await api.post(`/log-sources/${id}/keys/rotate`);
  return data.data;
}

export async function deleteLogSource(id) {
  await api.delete(`/log-sources/${id}`);
}

export const LOG_FORMATS = [
  { value: '', label: 'Auto (from source type)' },
  { value: 'ACCESS_LOG', label: 'nginx / Apache access log' },
  { value: 'AUTH_LOG', label: 'Linux auth.log' },
  { value: 'JSON_LINES', label: 'JSON lines' },
  { value: 'CSV', label: 'CSV (with header row)' },
];

export async function uploadLogFile(id, file, format) {
  const form = new FormData();
  form.append('file', file);
  if (format) form.append('format', format);
  const { data } = await api.post(`/log-sources/${id}/upload`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data;
}
