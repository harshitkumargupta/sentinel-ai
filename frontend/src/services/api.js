import axios from 'axios';
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './tokenStore.js';

// Base Axios instance. In dev, Vite proxies "/api" to the Spring Boot backend.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

// A bare client for the refresh call, so it doesn't recurse through the interceptors.
const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing = null;

// On 401, try a single refresh and replay the original request once.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;

    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh');

    if (status === 401 && !original._retry && !isAuthCall && getRefreshToken()) {
      original._retry = true;
      try {
        refreshing = refreshing || doRefresh();
        const newToken = await refreshing;
        refreshing = null;
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch (e) {
        refreshing = null;
        clearTokens();
        return Promise.reject(e);
      }
    }
    return Promise.reject(error);
  },
);

async function doRefresh() {
  const { data } = await refreshClient.post('/auth/refresh', {
    refreshToken: getRefreshToken(),
  });
  const payload = data.data;
  setTokens(payload.accessToken, payload.refreshToken);
  return payload.accessToken;
}

export default api;
