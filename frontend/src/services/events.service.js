import api from './api.js';

export async function listEvents(params = {}) {
  const { data } = await api.get('/events', { params });
  return data.data; // PageResponse
}

export async function getEvent(id) {
  const { data } = await api.get(`/events/${id}`);
  return data.data;
}
