import { createContext, useState, useEffect, useCallback } from 'react';
import authApi from '../api/authApi';
import { setTokens, clearTokens, getAccessToken } from '../utils/token';
import { isTokenExpired, decodeToken } from '../utils/auth';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On mount: check if there is a valid stored token and restore user state
  useEffect(() => {
    function initAuth() {
      const token = getAccessToken();
      if (token && !isTokenExpired(token)) {
        try {
          const savedUser = localStorage.getItem('denhub_user');
          if (savedUser && savedUser !== 'undefined') {
            setCurrentUser(JSON.parse(savedUser));
          } else {
            const decoded = decodeToken(token);
            if (decoded) {
              const authorities = decoded.authorities || [];
              const userObj = {
                id: decoded.user?.id || null,
                email: decoded.sub || decoded.user?.email || 'User',
                displayName: decoded.sub?.split('@')[0] || 'User',
                authorities,
                role: authorities.includes('ROLE_ADMIN') ? 'ADMIN' : 'USER',
              };
              setCurrentUser(userObj);
            }
          }
        } catch {
          clearTokens();
          localStorage.removeItem('denhub_user');
          setCurrentUser(null);
        }
      } else {
        clearTokens();
        localStorage.removeItem('denhub_user');
        setCurrentUser(null);
      }
      setLoading(false);
    }
    initAuth();
  }, []);

  const login = useCallback(async (credentials) => {
    const res = await authApi.login(credentials);
    // Backend wraps response in ResFormatResponse: { statusCode: 200, data: { accessToken, user, ... } }
    const payload = res.data?.data || res.data;
    const accessToken = payload?.accessToken;
    const refreshToken = payload?.refreshToken;
    const user = payload?.user;

    if (!accessToken) {
      throw new Error('Không nhận được Access Token hợp lệ từ máy chủ');
    }

    setTokens(accessToken, refreshToken);
    
    // Normalize user object
    const authorities = Array.isArray(user?.authorities) 
      ? user.authorities 
      : (typeof user?.authorities === 'string' && user.authorities ? [user.authorities] : []);
    
    const normalizedUser = {
      id: user?.id,
      email: user?.email,
      name: user?.name,
      displayName: user?.name || user?.email?.split('@')[0] || 'User',
      authorities,
      role: authorities.includes('ROLE_ADMIN') ? 'ADMIN' : 'USER',
    };

    localStorage.setItem('denhub_user', JSON.stringify(normalizedUser));
    setCurrentUser(normalizedUser);
    return normalizedUser;
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    localStorage.removeItem('denhub_user');
    setCurrentUser(null);
  }, []);

  const value = {
    currentUser,
    isAuthenticated: !!currentUser,
    role: currentUser?.role || (currentUser?.authorities?.includes('ROLE_ADMIN') ? 'ADMIN' : 'USER'),
    loading,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
