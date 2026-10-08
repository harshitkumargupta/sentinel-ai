import api from './api.js';

export async function listReportTypes() {
  const { data } = await api.get('/reports/types');
  return data.data;
}

export async function generateReport(body) {
  const { data } = await api.post('/reports/generate', body);
  return data.data;
}

export async function listReports() {
  const { data } = await api.get('/reports');
  return data.data;
}

export async function deleteReport(id) {
  await api.delete(`/reports/${id}`);
}

/** Download a stored report through Axios (auth header) as a file. */
export async function downloadReport(report) {
  const res = await api.get(`/reports/${report.id}/download`, { responseType: 'blob' });
  const disposition = res.headers['content-disposition'] || '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = match ? match[1] : `report-${report.id}.${report.format.toLowerCase()}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export async function listSchedules() {
  const { data } = await api.get('/reports/schedules');
  return data.data;
}

export async function saveSchedule(id, body) {
  const { data } = id ? await api.put(`/reports/schedules/${id}`, body) : await api.post('/reports/schedules', body);
  return data.data;
}

export async function deleteSchedule(id) {
  await api.delete(`/reports/schedules/${id}`);
}

export async function runSchedule(id) {
  const { data } = await api.post(`/reports/schedules/${id}/run`);
  return data.data;
}
