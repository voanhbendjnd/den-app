/**
 * Utility functions for Server Member management
 */

/**
 * Validates whether the current logged-in user can remove a target member from the server.
 *
 * Rules:
 * - Caller must be authenticated and must be the Server Owner (server.ownerId === currentUser.id).
 * - Target member cannot be the Server Owner.
 * - Server Owner cannot kick themselves.
 *
 * @param {Object} server Server object with id and ownerId
 * @param {Object} currentUser Current user object with id
 * @param {Object} targetMember Target server member object with userId and optional role
 * @returns {{ canRemove: boolean, reason: string | null }}
 */
export function canRemoveMember(server, currentUser, targetMember) {
  if (!server || !currentUser || !targetMember) {
    return { canRemove: false, reason: 'Dữ liệu không hợp lệ.' };
  }

  const isCallerOwner =
    Boolean(server.ownerId && currentUser.id) &&
    String(server.ownerId) === String(currentUser.id);

  if (!isCallerOwner) {
    return {
      canRemove: false,
      reason: 'Chỉ chủ sở hữu Server mới có quyền loại thành viên.',
    };
  }

  const isSelf =
    Boolean(targetMember.userId && currentUser.id) &&
    String(targetMember.userId) === String(currentUser.id);

  if (isSelf) {
    return {
      canRemove: false,
      reason: 'Chủ sở hữu không thể tự loại mình khỏi Server.',
    };
  }

  const isTargetOwner =
    targetMember.role === 'OWNER' ||
    (Boolean(server.ownerId && targetMember.userId) &&
      String(server.ownerId) === String(targetMember.userId));

  if (isTargetOwner) {
    return {
      canRemove: false,
      reason: 'Không thể loại chủ sở hữu khỏi Server.',
    };
  }

  return { canRemove: true, reason: null };
}

/**
 * Maps API errors from removeMember request to friendly, localized messages.
 *
 * @param {any} err Axios error or standard error
 * @param {string} [targetName] Display name of the member being removed
 * @returns {string} Friendly error message
 */
export function getRemoveMemberErrorMessage(err, targetName = 'thành viên') {
  if (!err) {
    return `Không thể loại ${targetName}. Vui lòng thử lại sau!`;
  }

  const status = err.response?.status;
  const data = err.response?.data;
  const message = data?.detail || data?.message || data?.errorKey || '';

  if (status === 401 || data?.errorKey === 'notloggedin') {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!';
  }

  if (status === 403) {
    return 'Bạn không có quyền loại thành viên này khỏi Server.';
  }

  if (status === 404) {
    return 'Thành viên hoặc Server không tồn tại hoặc đã bị xóa trước đó.';
  }

  if (status === 400) {
    const lower = String(message).toLowerCase();
    if (lower.includes('chủ sở hữu') || lower.includes('owner')) {
      return 'Không thể loại chủ sở hữu khỏi Server.';
    }
    if (lower.includes('không tồn tại') || lower.includes('not found') || lower.includes('not a member')) {
      return 'Thành viên không còn thuộc Server này.';
    }
    if (data?.message && typeof data.message === 'string' && data.message.trim().length > 0) {
      return data.message;
    }
    return 'Yêu cầu loại thành viên không hợp lệ.';
  }

  if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
    return 'Yêu cầu quá thời gian chờ. Vui lòng kiểm tra lại kết nối mạng!';
  }

  if (err.code === 'ERR_NETWORK' || !err.response) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!';
  }

  return `Không thể loại ${targetName}. Vui lòng thử lại sau!`;
}
