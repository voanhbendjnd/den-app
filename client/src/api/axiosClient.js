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

    if (status === 401) {
      // Token is invalid or expired — clear auth and redirect to login
      clearTokens();
      window.location.href = '/login';
    }

    // Let individual callers handle 400, 403, 404, 409, 500, etc.
    return Promise.reject(error);
  }
);

export default axiosClient;
