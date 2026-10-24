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

/**
 * Kiểm tra xem kênh có đủ điều kiện để xóa hay không:
 * - Kênh #general là mặc định, không thể xóa
 * - Server phải có ít nhất 1 kênh (không thể xóa kênh cuối cùng)
 */
export function canDeleteChannel(channel, totalChannelsCount) {
  if (!channel || !channel.id) {
    return { canDelete: false, reason: 'Thông tin kênh không hợp lệ!' };
  }
  if (channel.name && channel.name.trim().toLowerCase() === 'general') {
    return { canDelete: false, reason: 'Kênh #general là kênh mặc định và không thể bị xóa!' };
  }
  if (totalChannelsCount !== undefined && totalChannelsCount !== null && totalChannelsCount <= 1) {
    return { canDelete: false, reason: 'Không thể xóa kênh duy nhất trong Server!' };
  }
  return { canDelete: true, reason: null };
}

/**
 * Ánh xạ mã lỗi khi xóa Channel từ API server sang thông báo người dùng thân thiện
 */
export function getChannelDeleteErrorMessage(err, channelName = '') {
  const errorData = err?.response?.data;
  const errorKey = errorData?.errorKey;
  const message = errorData?.message || '';

  if (errorKey === 'cannotdeletegeneral' || message.includes('general')) {
    return 'Kênh #general là kênh mặc định và không thể bị xóa!';
  }
  if (errorKey === 'cannotdeletelastchannel' || message.includes('cuối cùng') || message.includes('last')) {
    return 'Không thể xóa kênh cuối cùng trong Server!';
  }
  if (errorKey === 'forbidden' || err?.response?.status === 403) {
    return 'Bạn không có quyền xóa kênh trong server này!';
  }
  if (errorKey === 'channelnotfound' || err?.response?.status === 404) {
    return 'Kênh không tồn tại hoặc đã bị xóa trước đó!';
  }
  if (errorKey === 'conflict' || err?.response?.status === 409) {
    return 'Không thể xóa kênh an toàn vì dữ liệu tin nhắn đang liên kết với hội thoại riêng!';
  }
  if (errorKey === 'invalidid') {
    return 'Mã kênh không hợp lệ!';
  }
  if (errorData?.message) {
    return errorData.message;
  }
  return channelName
    ? `Không thể xóa kênh "#${channelName}". Vui lòng thử lại sau!`
    : 'Không thể xóa kênh. Vui lòng thử lại sau!';
}

