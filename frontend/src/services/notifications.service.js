import api from './api.js';

const S = '/notification-settings';
export const myNotifications = async () => (await api.get('/notifications')).data.data;
export const markAllRead = async () => (await api.post('/notifications/read-all')).data.data;
export const listChannels = async () => (await api.get(`${S}/channels`)).data.data;
export const saveChannel = async (id, body) => (id ? await api.put(`${S}/channels/${id}`, body) : await api.post(`${S}/channels`, body)).data.data;
export const deleteChannel = async (id) => api.delete(`${S}/channels/${id}`);
export const testChannel = async (id) => (await api.post(`${S}/channels/${id}/test`)).data.data;
export const listNotifyRules = async () => (await api.get(`${S}/rules`)).data.data;
export const saveNotifyRule = async (id, body) => (id ? await api.put(`${S}/rules/${id}`, body) : await api.post(`${S}/rules`, body)).data.data;
export const deleteNotifyRule = async (id) => api.delete(`${S}/rules/${id}`);
export const listDeliveries = async () => (await api.get(`${S}/deliveries`)).data.data;
