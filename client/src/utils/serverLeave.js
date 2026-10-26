/**
 * Determines whether the current user is eligible to leave the server.
 * Per BR-SRV-01, the server owner cannot leave their own server.
 *
 * @param {Object} server
 * @param {Object} currentUser
 * @returns {{ canLeave: boolean, isOwner: boolean, reason: string | null }}
 */
export function canLeaveServer(server, currentUser) {
  if (!server || !currentUser) {
    return { canLeave: false, isOwner: false, reason: 'Thông tin Server hoặc tài khoản không hợp lệ.' };
  }

  const isOwner = Boolean(
    server.ownerId && currentUser.id && String(server.ownerId) === String(currentUser.id)
  );

  if (isOwner) {
    return {
      canLeave: false,
      isOwner: true,
      reason: 'Chủ sở hữu không thể tự rời Server. Hãy chuyển quyền sở hữu hoặc xóa Server.',
    };
  }

  return {
    canLeave: true,
    isOwner: false,
    reason: null,
  };
}

/**
 * Safely maps API error responses into localized, user-friendly error messages.
 * Never leaks raw exceptions or stack traces to the UI.
 *
 * @param {any} error
 * @param {string} [serverName]
 * @returns {string}
 */
export function getLeaveErrorMessage(error, serverName = 'Server') {
  if (!error) {
    return `Không thể rời khỏi ${serverName}. Vui lòng thử lại!`;
  }

  if (error.code === 'ECONNABORTED' || (typeof error.message === 'string' && error.message.toLowerCase().includes('timeout'))) {
    return 'Yêu cầu rời Server đã hết thời gian chờ. Vui lòng kiểm tra lại kết nối mạng!';
  }

  if (error.code === 'ERR_NETWORK' || !error.response) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!';
  }

  const { status, data } = error.response;
  const message = data?.message || '';
  const errorKey = data?.errorKey || '';

  if (status === 401 || errorKey === 'notloggedin') {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!';
  }

  if (status === 403) {
    return `Bạn không có quyền thực hiện thao tác rời khỏi ${serverName}.`;
  }

  if (status === 404) {
    return `${serverName} không tồn tại hoặc đã bị xóa trước đó.`;
  }

  if (status === 400) {
    const lowerMsg = message.toLowerCase();
    if (errorKey === 'ownerCannotLeave' || lowerMsg.includes('owner') || lowerMsg.includes('chủ sở hữu')) {
      return 'Chủ sở hữu không thể rời Server. Hãy chuyển quyền sở hữu hoặc xóa Server.';
    }
    if (errorKey === 'notMember' || lowerMsg.includes('not a member') || lowerMsg.includes('không phải là thành viên')) {
      return `Bạn không còn là thành viên của ${serverName}.`;
    }
    if (lowerMsg.includes('invalid') || lowerMsg.includes('id')) {
      return 'Mã Server không hợp lệ.';
    }
    if (message) {
      return message;
    }
  }

  return `Không thể rời khỏi ${serverName}. Vui lòng thử lại sau!`;
}

/**
 * Removes a server from the server list by its ID, supporting both string and number types.
 *
 * @param {Array<Object>} servers
 * @param {string | number} serverIdToRemove
 * @returns {Array<Object>}
 */
export function removeServerFromList(servers, serverIdToRemove) {
  if (!Array.isArray(servers) || serverIdToRemove === null || serverIdToRemove === undefined) {
    return Array.isArray(servers) ? [...servers] : [];
  }
  const targetId = String(serverIdToRemove);
  return servers.filter((s) => String(s?.id) !== targetId);
}

/**
 * Checks whether a server is present in the server list.
 *
 * @param {Array<Object>} servers
 * @param {string | number} serverId
 * @returns {boolean}
 */
export function isServerInList(servers, serverId) {
  if (!Array.isArray(servers) || serverId === null || serverId === undefined) {
    return false;
  }
  const targetId = String(serverId);
  return servers.some((s) => String(s?.id) === targetId);
}
