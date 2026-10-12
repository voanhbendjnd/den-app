const ACCESS_TOKEN_KEY = 'denhub_access_token';
const REFRESH_TOKEN_KEY = 'denhub_refresh_token';

export function getAccessToken() {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (!token || token === 'undefined' || token === 'null') return null;
  return token;
}

export function getRefreshToken() {
  const token = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!token || token === 'undefined' || token === 'null') return null;
  return token;
}

export function setTokens(accessToken, refreshToken) {
  if (accessToken && accessToken !== 'undefined' && accessToken !== 'null') {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  } else {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }
  if (refreshToken && refreshToken !== 'undefined' && refreshToken !== 'null') {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}
