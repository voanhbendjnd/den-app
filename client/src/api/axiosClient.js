import axios from 'axios';
import { getAccessToken, clearTokens } from '../utils/token';

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach access token to every request
axiosClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle common HTTP errors globally
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url || '';

    // If 401 is encountered on a protected endpoint, clear session and redirect.
    // NEVER redirect on the login endpoint itself so the login page can display the error!
    if (status === 401 && !requestUrl.includes('/login')) {
      clearTokens();
      localStorage.removeItem('denhub_user');
      window.location.href = '/login';
    }

    return Promise.reject(error);
  }
);

export default axiosClient;
