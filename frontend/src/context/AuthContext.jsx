import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { login as loginApi, logout as logoutApi, fetchMe, hasStoredSession } from '../services/auth.service.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On first load, if a refresh token exists, try to restore the session.
  useEffect(() => {
    let active = true;
    async function restore() {
      if (hasStoredSession()) {
        try {
          const me = await fetchMe();
          if (active) setUser(me);
        } catch {
          /* token invalid/expired */
        }
      }
      if (active) setLoading(false);
    }
    restore();
    return () => {
      active = false;
    };
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      isAuthenticated: Boolean(user),
      async login(username, password) {
        const u = await loginApi(username, password);
        setUser(u);
        return u;
      },
      async logout() {
        await logoutApi();
        setUser(null);
      },
      hasRole(...roles) {
        return user != null && roles.includes(user.role);
      },
    }),
    [user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
