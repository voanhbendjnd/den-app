/**
 * Utility functions for user profile data formatting, validation and error handling.
 */

/**
 * Định dạng ngày tham gia hệ thống
 * @param {string|number|Date} dateInput 
 * @param {boolean} verbose - Nếu true, định dạng 'DD tháng MM, YYYY', false thì 'DD/MM/YYYY'
 * @returns {string}
 */
export function formatJoinDate(dateInput, verbose = false) {
  if (!dateInput) return 'Chưa xác định';

  const date = new Date(dateInput);
  if (Number.isNaN(date.getTime())) {
    return 'Chưa xác định';
  }

  const day = String(date.getDate()).padStart(2, '0');
  const month = date.getMonth() + 1;
  const year = date.getFullYear();

  if (verbose) {
    return `${day} tháng ${month}, ${year}`;
  }

  const paddedMonth = String(month).padStart(2, '0');
  return `${day}/${paddedMonth}/${year}`;
}

/**
 * Bóc tách dữ liệu thông tin cá nhân từ response của backend
 * @param {any} response - Axios response hoặc response payload
 * @returns {object|null}
 */
export function extractProfileData(response) {
  if (!response) return null;

  // Hỗ trợ cả trường hợp truyền trọn vẹn Axios response hoặc data payload
  let data = response.data !== undefined ? response.data : response;

  // Backend ResFormatResponse: { statusCode: 200, message: "...", data: { ... } }
  if (data && typeof data === 'object' && data.data !== undefined) {
    data = data.data;
  }

  if (!data || typeof data !== 'object') {
    return null;
  }

  const authorities = Array.isArray(data.authorities)
    ? data.authorities
    : (typeof data.authorities === 'string' && data.authorities ? [data.authorities] : ['ROLE_USER']);

  const email = data.email || '';
  const username = data.username || (email ? email.split('@')[0] : 'user');
  const name = data.name || data.displayName || username;

  return {
    id: data.id ?? null,
    email,
    name,
    username,
    avatar: data.avatar || null,
    activated: data.activated !== undefined ? Boolean(data.activated) : true,
    createdDate: data.createdDate || null,
    authorities,
  };
}

/**
 * Ánh xạ lỗi khi gọi API tải hồ sơ cá nhân sang thông điệp tiếng Việt thân thiện
 * @param {any} error 
 * @returns {string}
 */
export function getProfileErrorMessage(error) {
  if (!error) return 'Không thể tải thông tin hồ sơ.';

  const status = error.response?.status;
  const serverMsg = error.response?.data?.message;

  if (status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  }

  if (status === 403) {
    return 'Bạn không có quyền truy cập hồ sơ này.';
  }

  if (status === 404) {
    return 'Không tìm thấy thông tin tài khoản người dùng.';
  }

  if (status >= 500) {
    return serverMsg || 'Máy chủ gặp sự cố khi xử lý dữ liệu hồ sơ. Vui lòng thử lại sau.';
  }

  if (error.code === 'ERR_NETWORK' || error.message === 'Network Error') {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra đường truyền mạng.';
  }

  return serverMsg || error.message || 'Không thể tải thông tin hồ sơ. Vui lòng thử lại.';
}

/**
 * Ánh xạ danh sách quyền (authorities) sang thông tin hiển thị UI
 * @param {string[]} authorities 
 * @returns {Array<{ authority: string, label: string, color: string, tagColor: string }>}
 */
export function getRoleDisplays(authorities = []) {
  const roles = Array.isArray(authorities) && authorities.length > 0
    ? authorities
    : ['ROLE_USER'];

  return roles.map((role) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return {
          authority: role,
          label: 'Quản trị viên',
          color: '#EF4444',
          tagColor: 'red',
        };
      case 'ROLE_MODERATOR':
        return {
          authority: role,
          label: 'Điều hành viên',
          color: '#F59E0B',
          tagColor: 'orange',
        };
      case 'ROLE_USER':
      default:
        return {
          authority: role,
          label: 'Thành viên',
          color: '#00F2FE',
          tagColor: 'cyan',
        };
    }
  });
}
