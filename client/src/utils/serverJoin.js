/**
 * Utility functions for validating Server ID / Invite Code and mapping join server errors.
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

/**
 * Validates either a numeric Server ID or an alphanumeric invite code / URL.
 */
export function validateJoinInput(rawValue) {
  if (rawValue === undefined || rawValue === null) {
    return 'Vui lòng nhập Mã mời hoặc Server ID.';
  }

  const str = String(rawValue).trim();
  if (str === '') {
    return 'Vui lòng nhập Mã mời hoặc Server ID.';
  }

  const parsed = parseJoinInput(str);
  if (!parsed) {
    return 'Mã mời hoặc Server ID không hợp lệ. Vui lòng nhập mã mời (ví dụ: aB9xK2Lm) hoặc mã số Server.';
  }

  return null;
}

/**
 * Parses user input into either an inviteCode or a numeric serverId.
 * Strips URLs or paths if user pasted a full invite URL (e.g. /invite/aB9xK2Lm).
 */
export function parseJoinInput(rawValue) {
  if (!rawValue) return null;
  let str = String(rawValue).trim();
  if (!str) return null;

  // If input contains path slashes, extract the last non-empty segment
  if (str.includes('/')) {
    const parts = str.split('/').filter(Boolean);
    if (parts.length > 0) {
      str = parts[parts.length - 1].trim();
    }
  }

  // Pure positive integer -> Server ID
  if (/^\d+$/.test(str)) {
    const num = Number(str);
    if (Number.isSafeInteger(num) && num > 0) {
      return { type: 'serverId', value: num };
    }
  }

  // Alphanumeric Base62 invite code (6 to 32 characters)
  if (/^[a-zA-Z0-9]{6,32}$/.test(str)) {
    return { type: 'inviteCode', value: str };
  }

  return null;
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
  const errorKey = (data.errorKey || '').toLowerCase();
  const messageStr = (data.message || data.detail || data.title || '').toLowerCase();

  // Specific domain errors from backend
  if (errorKey === 'alreadymember' || messageStr.includes('already a member')) {
    return 'Bạn đã là thành viên của Server này rồi!';
  }

  if (errorKey === 'invitenotfound' || messageStr.includes('invite code not found')) {
    return 'Mã mời không tồn tại hoặc không chính xác!';
  }

  if (errorKey === 'inviteexpired' || messageStr.includes('invite code is expired')) {
    return 'Mã mời này đã hết hạn sử dụng!';
  }

  if (errorKey === 'inviterevoked' || messageStr.includes('invite code is revoked')) {
    return 'Mã mời này đã bị vô hiệu hóa!';
  }

  if (errorKey === 'servernotfound' || status === 404 || messageStr.includes('server not found')) {
    return 'Không tìm thấy Server với thông tin này. Vui lòng kiểm tra lại!';
  }

  if (errorKey === 'notloggedin' || status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!';
  }

  if (status === 403) {
    return 'Server riêng tư yêu cầu mã mời hợp lệ, hoặc bạn không có quyền tham gia Server này.';
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
