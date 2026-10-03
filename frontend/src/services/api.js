import axios from 'axios';

// Base Axios instance. In dev, Vite proxies "/api" to the Spring Boot backend
// (see vite.config.js). In prod, set VITE_API_BASE_URL at build time.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Attach the auth token (set once the auth phase lands).
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('sentinel.token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
