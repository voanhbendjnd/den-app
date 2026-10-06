import { createContext, useState, useEffect, useCallback } from 'react';
import authApi from '../api/authApi';
import { setTokens, clearTokens, getAccessToken } from '../utils/token';
import { isTokenExpired } from '../utils/auth';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(true); // true while checking stored token

  // On mount: check if there is a valid stored token and fetch user info
  useEffect(() => {
    async function initAuth() {
      const token = getAccessToken();
      if (token && !isTokenExpired(token)) {
        try {
          const res = await authApi.getMe();
          setCurrentUser(res.data);
        } catch {
          clearTokens();
          setCurrentUser(null);
        }
      } else {
        clearTokens();
        setCurrentUser(null);
      }
      setLoading(false);
    }
    initAuth();
  }, []);

  const login = useCallback(async (credentials) => {
    const res = await authApi.login(credentials);
    const { accessToken, refreshToken, user } = res.data;
    setTokens(accessToken, refreshToken);
    setCurrentUser(user);
    return user;
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    setCurrentUser(null);
  }, []);

  const value = {
    currentUser,
    isAuthenticated: !!currentUser,
    role: currentUser?.role || null,
    loading,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
