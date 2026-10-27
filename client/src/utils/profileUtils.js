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

/**
 * Biểu thức chính quy cho username: 3-50 ký tự, chỉ gồm chữ cái, chữ số, _, -, .
 */
export const USERNAME_REGEX = /^[a-zA-Z0-9_.-]{3,50}$/;

/**
 * Kiểm tra tính hợp lệ của dữ liệu cập nhật hồ sơ trước khi gửi API
 * @param {object} values { name, username, avatar }
 * @returns {{ isValid: boolean, errors: Record<string, string> }}
 */
export function validateProfileUpdate(values = {}) {
  const errors = {};

  // Validate Name
  if (values.name !== undefined && values.name !== null) {
    const trimmedName = String(values.name).trim();
    if (values.name && trimmedName.length === 0) {
      errors.name = 'Tên hiển thị không được chỉ chứa khoảng trắng.';
    } else if (values.name.length > 50) {
      errors.name = 'Tên hiển thị không được vượt quá 50 ký tự.';
    }
  }

  // Validate Username
  if (values.username !== undefined && values.username !== null) {
    const trimmedUsername = String(values.username).trim();
    if (trimmedUsername.length === 0) {
      errors.username = 'Tên người dùng không được để trống.';
    } else if (trimmedUsername.length < 3 || trimmedUsername.length > 50) {
      errors.username = 'Tên người dùng phải có độ dài từ 3 đến 50 ký tự.';
    } else if (!USERNAME_REGEX.test(trimmedUsername)) {
      errors.username = 'Tên người dùng chỉ được chứa chữ cái, chữ số, gạch dưới (_), gạch ngang (-) hoặc dấu chấm (.).';
    }
  }

  // Validate Avatar URL
  if (values.avatar !== undefined && values.avatar !== null) {
    const trimmedAvatar = String(values.avatar).trim();
    if (trimmedAvatar.length > 500) {
      errors.avatar = 'Đường dẫn ảnh đại diện không được vượt quá 500 ký tự.';
    } else if (trimmedAvatar.length > 0) {
      const isValidUrl = /^https?:\/\/.+/i.test(trimmedAvatar) || /^data:image\/.+/i.test(trimmedAvatar);
      if (!isValidUrl) {
        errors.avatar = 'Đường dẫn ảnh đại diện phải bắt đầu bằng http:// hoặc https://';
      }
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Chuẩn hóa dữ liệu cập nhật trước khi gửi request tới API
 * @param {object} values { name, username, avatar }
 * @returns {object}
 */
export function sanitizeProfilePayload(values = {}) {
  const payload = {};

  if (values.name !== undefined && values.name !== null) {
    payload.name = String(values.name).trim();
  }

  if (values.username !== undefined && values.username !== null) {
    payload.username = String(values.username).trim().toLowerCase();
  }

  if (values.avatar !== undefined && values.avatar !== null) {
    const trimmed = String(values.avatar).trim();
    payload.avatar = trimmed.length > 0 ? trimmed : null;
  }

  return payload;
}

/**
 * Ánh xạ lỗi trả về từ API cập nhật hồ sơ cá nhân sang thông điệp tiếng Việt thân thiện
 * @param {any} error 
 * @param {string} [username] 
 * @returns {string}
 */
export function getProfileUpdateErrorMessage(error, username = '') {
  if (!error) return 'Cập nhật hồ sơ thất bại.';

  const data = error.response?.data;
  const status = error.response?.status;
  const errorKey = data?.errorKey || data?.title;
  const serverMsg = data?.message || data?.detail;

  // Lỗi trùng lặp username
  if (
    errorKey === 'usernamealreadyexists' ||
    (typeof serverMsg === 'string' && (
      serverMsg.toLowerCase().includes('username already in use') ||
      serverMsg.toLowerCase().includes('username already exists')
    ))
  ) {
    const formattedUsername = username ? `@${username}` : 'này';
    return `Tên người dùng ${formattedUsername} đã có người sử dụng. Vui lòng chọn tên khác.`;
  }

  // Lỗi chưa đăng nhập hoặc không tìm thấy user
  if (errorKey === 'notloggedin' || status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  }

  if (errorKey === 'usernotfound' || status === 404) {
    return 'Không tìm thấy thông tin tài khoản người dùng.';
  }

  if (status === 403) {
    return 'Bạn không có quyền cập nhật thông tin hồ sơ này.';
  }

  // Lỗi validation từ Bean Validation (fieldErrors)
  if (data?.fieldErrors && Array.isArray(data.fieldErrors) && data.fieldErrors.length > 0) {
    const firstErr = data.fieldErrors[0];
    const fieldName = firstErr.field === 'name'
      ? 'Tên hiển thị'
      : firstErr.field === 'username'
      ? 'Tên người dùng'
      : firstErr.field === 'avatar'
      ? 'Ảnh đại diện'
      : firstErr.field;
    return `${fieldName}: ${firstErr.message || 'Dữ liệu không hợp lệ.'}`;
  }

  if (status >= 500) {
    return serverMsg || 'Máy chủ gặp sự cố khi lưu hồ sơ. Vui lòng thử lại sau.';
  }

  if (error.code === 'ERR_NETWORK' || error.message === 'Network Error') {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra đường truyền mạng.';
  }

  return serverMsg || error.message || 'Cập nhật hồ sơ thất bại. Vui lòng thử lại.';
}

