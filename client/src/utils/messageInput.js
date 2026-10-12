export const MAX_MESSAGE_LENGTH = 4000;

export function getMessageValidationError(content) {
  // Java's (?U) whitespace includes U+0085; JavaScript trim() does not.
  if (typeof content !== 'string' || !/[^\p{White_Space}]/u.test(content)) {
    return 'Nhập nội dung tin nhắn trước khi gửi.';
  }
  if (content.length > MAX_MESSAGE_LENGTH) {
    return 'Tin nhắn vượt giới hạn 4000. Hãy rút gọn nội dung.';
  }
  return null;
}

export function getMessageSendError(error) {
  // Keep infrastructure details out of the UI, including raw backend errors.
  switch (error?.response?.status) {
    case 400: return 'Nội dung chưa hợp lệ. Hãy kiểm tra và gửi lại.';
    case 401: return 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại để gửi.';
    case 403: return 'Bạn không có quyền gửi tin nhắn trong kênh này.';
    case 404: return 'Không tìm thấy kênh này. Hãy chọn lại kênh.';
    default: return 'Chưa gửi được tin nhắn. Nội dung được giữ lại để bạn thử lại.';
  }
}
