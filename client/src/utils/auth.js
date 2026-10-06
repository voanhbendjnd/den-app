import { jwtDecode } from 'jwt-decode';
import { getAccessToken } from './token';

/**
 * Decodes the stored access token.
 * NOTE: jwt-decode only READS claims. It does NOT verify the signature.
 * Real JWT verification happens on the Spring Boot backend.
 */
export function decodeToken(token) {
  try {
    return jwtDecode(token);
  } catch {
    return null;
  }
}

export function getCurrentUserFromToken() {
  const token = getAccessToken();
  if (!token) return null;
  return decodeToken(token);
}

export function isTokenExpired(token) {
  const decoded = decodeToken(token);
  if (!decoded || !decoded.exp) return true;
  return decoded.exp * 1000 < Date.now();
}
