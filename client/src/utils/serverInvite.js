/**
 * Utility functions for Server Invite creation, error mapping, and clipboard handling.
 */

/**
 * Extracts and validates invite data from the Axios response wrapper (RestResponse).
 * @param {any} response - Axios response or response data object.
 * @returns {{ id?: number, serverId?: number, code: string, createdBy?: number, expiresAt?: string | null, status?: string, createdAt?: string }}
 */
export function extractInviteResponse(response) {
  if (!response) {
    throw new Error('Không nhận được phản hồi từ máy chủ.');
  }

  const payload = response.data !== undefined ? response.data : response;
  const inviteData = payload && typeof payload === 'object' && payload.data !== undefined ? payload.data : payload;

  if (!inviteData || typeof inviteData !== 'object') {
    throw new Error('Dữ liệu mã mời trả về không hợp lệ.');
  }

  const code = inviteData.code;
  if (!code || typeof code !== 'string' || !code.trim()) {
    throw new Error('Dữ liệu mã mời trả về không hợp lệ (thiếu mã code).');
  }

  return inviteData;
}

/**
 * Copies text to the clipboard with fallback for non-secure / unsupported environments.
 * @param {string} text - The text string to copy.
 * @returns {Promise<{ success: boolean, error?: string }>}
 */
export async function copyToClipboard(text) {
  if (!text || typeof text !== 'string') {
    return { success: false, error: 'Không có nội dung để sao chép.' };
  }

  // 1. Try modern navigator.clipboard API if available
  if (typeof navigator !== 'undefined' && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
    try {
      await navigator.clipboard.writeText(text);
      return { success: true };
    } catch {
      // Fallback below
    }
  }

  // 2. Fallback using temporary textarea element and document.execCommand
  if (typeof document !== 'undefined' && typeof document.createElement === 'function') {
    try {
      const textArea = document.createElement('textarea');
      textArea.value = text;
      textArea.style.position = 'fixed';
      textArea.style.left = '-9999px';
      textArea.style.top = '-9999px';
      textArea.setAttribute('readonly', '');
      document.body.appendChild(textArea);
      textArea.select();
      const successful = document.execCommand('copy');
      document.body.removeChild(textArea);
      if (successful) {
        return { success: true };
      }
    } catch {
      // Failed fallback
    }
  }

  return {
    success: false,
    error: 'Không thể tự động sao chép. Vui lòng chọn và sao chép thủ công.',
  };
}

/**
 * Maps API / network error to user-friendly Vietnamese message.
 * @param {any} err - The Axios or standard error object.
 * @returns {string} User-facing error message.
 */
export function getInviteErrorMessage(err) {
  if (!err) {
    return 'Không thể tạo mã mời. Vui lòng thử lại!';
  }

  // Check network error
  if (err.code === 'ERR_NETWORK' || !err.response) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!';
  }

  const status = err.response.status;
  const data = err.response.data || {};
  const errorKey = data.errorKey || '';
  const messageStr = (data.message || data.detail || data.title || '').toLowerCase();

  if (status === 401 || errorKey === 'notloggedin' || messageStr.includes('unauthorized')) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!';
  }

  if (status === 403 || errorKey === 'forbidden' || messageStr.includes('chủ sở hữu')) {
    return 'Chỉ chủ sở hữu Server mới có quyền tạo mã mời!';
  }

  if (status === 404 || errorKey === 'servernotfound' || messageStr.includes('không tìm thấy server')) {
    return 'Server không tồn tại hoặc đã bị xóa!';
  }

  if (status === 400) {
    if (messageStr.includes('quá khứ')) {
      return 'Thời gian hết hạn của mã mời không được ở trong quá khứ!';
    }
    if (messageStr.includes('id') || messageStr.includes('server')) {
      return 'Mã Server không hợp lệ.';
    }
    return data.detail || data.message || 'Yêu cầu tạo mã mời không hợp lệ!';
  }

  if (typeof data.detail === 'string' && data.detail.trim()) {
    return data.detail;
  }

  if (typeof data.message === 'string' && data.message.trim()) {
    return data.message;
  }

  return 'Không thể tạo mã mời. Vui lòng thử lại sau!';
}

/**
 * Formats an ISO expiration date string into human-readable Vietnamese.
 * @param {string | null | undefined} expiresAt
 * @returns {string}
 */
export function formatExpiresAt(expiresAt) {
  if (!expiresAt) {
    return 'Vĩnh viễn (Không hết hạn)';
  }

  try {
    const d = new Date(expiresAt);
    if (isNaN(d.getTime())) {
      return 'Vĩnh viễn (Không hết hạn)';
    }

    const pad = (n) => String(n).padStart(2, '0');
    const hours = pad(d.getHours());
    const minutes = pad(d.getMinutes());
    const day = pad(d.getDate());
    const month = pad(d.getMonth() + 1);
    const year = d.getFullYear();

    return `${hours}:${minutes} ngày ${day}/${month}/${year}`;
  } catch {
    return 'Vĩnh viễn (Không hết hạn)';
  }
}
