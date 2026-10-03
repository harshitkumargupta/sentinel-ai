import api from './api.js';
import { setTokens, clearTokens, getRefreshToken } from './tokenStore.js';

export async function login(username, password) {
  const { data } = await api.post('/auth/login', { username, password });
  const payload = data.data;
  setTokens(payload.accessToken, payload.refreshToken);
  return payload.user;
}

export async function fetchMe() {
  const { data } = await api.get('/auth/me');
  return data.data;
}

export async function logout() {
  try {
    await api.post('/auth/logout');
  } catch {
    /* best-effort; clear locally regardless */
  }
  clearTokens();
}

export function hasStoredSession() {
  return Boolean(getRefreshToken());
}
