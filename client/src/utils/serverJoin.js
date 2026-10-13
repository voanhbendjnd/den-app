/**
 * Utility functions for validating Server ID and mapping join server errors.
 */

export function validateServerId(rawValue) {
  if (rawValue === undefined || rawValue === null) {
    return 'Vui lòng nhập ID của Server.';
  }

  const str = String(rawValue).trim();
  if (str === '') {
    return 'Vui lòng nhập ID của Server.';
  }

  // Must consist only of digits and be greater than 0
  if (!/^\d+$/.test(str)) {
    return 'Mã Server ID phải là số nguyên dương hợp lệ.';
  }

  const num = Number(str);
  if (!Number.isSafeInteger(num) || num <= 0) {
    return 'Mã Server ID phải là số nguyên dương hợp lệ.';
  }

  return null;
}

export function parseServerId(rawValue) {
  if (!rawValue) return null;
  const str = String(rawValue).trim();
  if (!/^\d+$/.test(str)) return null;
  return Number(str);
}

export function getJoinErrorMessage(err) {
  if (!err) {
    return 'Không thể tham gia Server. Vui lòng thử lại!';
  }

  // Check network error
  if (err.code === 'ERR_NETWORK' || !err.response) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!';
  }

  const status = err.response.status;
  const data = err.response.data || {};
  const errorKey = data.errorKey || '';
  const messageStr = (data.message || data.detail || data.title || '').toLowerCase();

  // Specific domain errors from backend
  if (errorKey === 'alreadymember' || messageStr.includes('already a member')) {
    return 'Bạn đã là thành viên của Server này rồi!';
  }

  if (errorKey === 'servernotfound' || status === 404 || messageStr.includes('server not found')) {
    return 'Không tìm thấy Server với ID này. Vui lòng kiểm tra lại!';
  }

  if (errorKey === 'notloggedin' || status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!';
  }

  if (status === 403) {
    return 'Bạn không có quyền tham gia Server này.';
  }

  // Field validation errors
  if (Array.isArray(data.fieldErrors) && data.fieldErrors.length > 0 && data.fieldErrors[0].message) {
    return data.fieldErrors[0].message;
  }

  if (typeof data.detail === 'string' && data.detail.trim()) {
    return data.detail;
  }

  if (typeof data.message === 'string' && data.message.trim()) {
    return data.message;
  }

  return 'Không thể tham gia Server. Vui lòng thử lại!';
}
