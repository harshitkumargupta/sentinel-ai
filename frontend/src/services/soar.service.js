import api from './api.js';

export const listPlaybooks = async () => (await api.get('/soar/playbooks')).data.data;
export const savePlaybook = async (id, body) => (id ? await api.put(`/soar/playbooks/${id}`, body) : await api.post('/soar/playbooks', body)).data.data;
export const deletePlaybook = async (id) => api.delete(`/soar/playbooks/${id}`);
export const runPlaybook = async (id, incidentId) => (await api.post(`/soar/playbooks/${id}/run`, { incidentId })).data.data;
export const listRuns = async () => (await api.get('/soar/runs')).data.data;
export const matchingPlaybooks = async (incidentId) => (await api.get(`/incidents/${incidentId}/playbooks`)).data.data;
