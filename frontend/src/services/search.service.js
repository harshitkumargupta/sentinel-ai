import api from './api.js';

export async function searchEvents(params = {}) {
  const { data } = await api.get('/search/events', { params });
  return data.data; // PageResponse<EventResponse>
}

export async function getSearchFields() {
  const { data } = await api.get('/search/events/fields');
  return data.data;
}

/** Download the CSV export (auth header via Axios, then a temporary object URL). */
export async function exportEventsCsv(params = {}) {
  const res = await api.get('/search/events/export', { params, responseType: 'blob' });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'sentinel-events.csv';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export async function listSavedSearches() {
  const { data } = await api.get('/saved-searches');
  return data.data;
}

export async function saveSearch(id, body) {
  const { data } = id ? await api.put(`/saved-searches/${id}`, body) : await api.post('/saved-searches', body);
  return data.data;
}

export async function deleteSavedSearch(id) {
  await api.delete(`/saved-searches/${id}`);
}

export async function pinnedSearchStats() {
  const { data } = await api.get('/saved-searches/pinned');
  return data.data; // [{ id, name, count, hourly[24], from, to }]
}
