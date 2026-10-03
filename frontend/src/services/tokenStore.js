// Access token is kept in memory (not persisted) for safety; the refresh token is kept in
// sessionStorage so a page reload can transparently re-establish a session.
let accessToken = null;

export function getAccessToken() {
  return accessToken;
}

export function getRefreshToken() {
  try {
    return sessionStorage.getItem('sentinel.refresh');
  } catch {
    return null;
  }
}

export function setTokens(access, refresh) {
  accessToken = access;
  try {
    if (refresh) {
      sessionStorage.setItem('sentinel.refresh', refresh);
    }
  } catch {
    /* ignore storage errors (private mode) */
  }
}

export function clearTokens() {
  accessToken = null;
  try {
    sessionStorage.removeItem('sentinel.refresh');
  } catch {
    /* ignore */
  }
}
