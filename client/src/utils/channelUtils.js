/**
 * Chuẩn hóa tên kênh tự động:
 * - Chuyển sang chữ thường
 * - Thay thế khoảng trắng thành dấu gạch ngang '-'
 * - Loại bỏ các ký tự đặc biệt ngoài chữ, số, dấu gạch ngang và dấu gạch dưới
 */
export function formatChannelName(raw) {
  if (!raw) return '';
  return raw
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9-_]/g, '');
}

/**
 * Kiểm tra tính hợp lệ của tên kênh
 * @returns {string|null} Thông báo lỗi nếu không hợp lệ, null nếu hợp lệ
 */
export function validateChannelName(name) {
  if (!name || !name.trim()) {
    return 'Vui lòng nhập tên kênh!';
  }
  const trimmed = name.trim();
  if (trimmed.length < 2) {
    return 'Tên kênh phải có ít nhất 2 ký tự!';
  }
  if (trimmed.length > 20) {
    return 'Tên kênh không được vượt quá 20 ký tự!';
  }
  if (!/^[a-z0-9-_]+$/.test(trimmed)) {
    return 'Tên kênh chỉ được chứa chữ thường không dấu, số và dấu gạch ngang/dưới!';
  }
  return null;
}

/**
 * Kiểm tra tính hợp lệ của vị trí thứ tự kênh
 * @returns {string|null} Thông báo lỗi nếu không hợp lệ, null nếu hợp lệ
 */
export function validateChannelPosition(position) {
  if (position === undefined || position === null || position === '') {
    return null; // Position is optional
  }
  const num = Number(position);
  if (!Number.isInteger(num) || num < 0) {
    return 'Thứ tự vị trí phải là số nguyên không âm (>= 0)!';
  }
  return null;
}

/**
 * Chuyển đổi mã lỗi từ API server sang thông báo người dùng thân thiện
 */
export function getChannelEditErrorMessage(err, channelName = '') {
  const errorData = err?.response?.data;
  if (
    errorData?.errorKey === 'namealreadyexists' ||
    errorData?.message?.includes('already exists') ||
    errorData?.title?.includes('already exists')
  ) {
    return `Tên kênh "#${channelName}" đã tồn tại trong server này. Vui lòng chọn tên khác!`;
  }
  if (errorData?.errorKey === 'forbidden') {
    return 'Bạn không có quyền chỉnh sửa kênh trong server này!';
  }
  if (errorData?.errorKey === 'channelnotfound') {
    return 'Kênh không tồn tại hoặc đã bị xóa!';
  }
  if (errorData?.violations?.length > 0) {
    return errorData.violations[0].message;
  }
  if (errorData?.message) {
    return errorData.message;
  }
  return 'Không thể cập nhật kênh. Vui lòng thử lại sau!';
}
