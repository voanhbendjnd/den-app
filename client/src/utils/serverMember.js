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

  const targetUserId = targetMember.userId ?? targetMember.id;
  if (!server.ownerId || !currentUser.id || !targetUserId) {
    return { canRemove: false, reason: 'Dữ liệu không hợp lệ.' };
  }

  const isCallerOwner = String(server.ownerId) === String(currentUser.id);

  if (!isCallerOwner) {
    return {
      canRemove: false,
      reason: 'Chỉ chủ sở hữu Server mới có quyền loại thành viên.',
    };
  }

  const isSelf = String(targetUserId) === String(currentUser.id);

  if (isSelf) {
    return {
      canRemove: false,
      reason: 'Chủ sở hữu không thể tự loại mình khỏi Server.',
    };
  }

  const isTargetOwner =
    targetMember.role === 'OWNER' ||
    String(server.ownerId) === String(targetUserId);

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

/**
 * Finds a member in a members list by user ID.
 *
 * @param {Array<Object>} members
 * @param {string | number} userId
 * @returns {Object | null}
 */
export function findMemberByUserId(members, userId) {
  if (!Array.isArray(members) || userId === null || userId === undefined) {
    return null;
  }
  return members.find((m) => String(m.userId ?? m.id) === String(userId)) || null;
}

/**
 * Safely merges a newly joined member into the current members list,
 * avoiding duplicate entries and preserving role consistency.
 *
 * @param {Array<Object>} currentMembers
 * @param {Object} newMember
 * @param {Object} [server] Optional server object to verify owner role
 * @returns {Array<Object>}
 */
export function mergeNewMemberIntoList(currentMembers, newMember, server = null) {
  if (!newMember) {
    return Array.isArray(currentMembers) ? [...currentMembers] : [];
  }
  const list = Array.isArray(currentMembers) ? [...currentMembers] : [];
  const targetId = String(newMember.userId ?? newMember.id ?? '');
  if (!targetId) {
    return list;
  }
  const exists = list.some((m) => String(m.userId ?? m.id) === targetId);
  if (exists) {
    return list;
  }
  const isOwner = Boolean(server?.ownerId && String(server.ownerId) === targetId);
  const normalizedMember = {
    ...newMember,
    role: isOwner ? 'OWNER' : (newMember.role || 'MEMBER'),
  };
  return [...list, normalizedMember];
}

/**
 * Formats the count of members for UI display.
 *
 * @param {Array<Object>} members
 * @returns {string}
 */
export function getMemberCountDisplay(members) {
  const count = Array.isArray(members) ? members.length : 0;
  return `Thành viên (${count})`;
}

/**
 * Formats a member's display name following priority: nickname > name > username > fallback.
 *
 * @param {Object} member
 * @returns {string}
 */
export function formatMemberDisplayName(member) {
  if (!member) return 'Thành viên';
  return member.nickname || member.name || member.username || 'Thành viên';
}

/**
 * Checks whether the current user is allowed access to a server based on ownership
 * or presence in the server members list.
 *
 * @param {Object} server Server object with id and ownerId
 * @param {Object} currentUser Current user object with id
 * @param {Array<Object>} [members] List of active server members
 * @returns {boolean} True if current user is owner or active member, false otherwise
 */
export function isMemberAllowedAccess(server, currentUser, members = null) {
  if (!server || !currentUser) {
    return false;
  }
  const currentUserId = currentUser.id ?? currentUser.userId;
  if (!currentUserId) {
    return false;
  }

  // Owner always has access
  if (server.ownerId && String(server.ownerId) === String(currentUserId)) {
    return true;
  }

  // If members list is loaded, verify membership
  if (Array.isArray(members)) {
    return members.some((m) => String(m.userId ?? m.id) === String(currentUserId));
  }

  return false;
}

/**
 * Maps API errors when a removed member attempts to access a server, its channels,
 * or its messages to a user-friendly Vietnamese notification message.
 *
 * @param {any} err Axios error or standard response error
 * @returns {string} Localized error message
 */
export function getRemovedMemberAccessErrorMessage(err) {
  if (!err) {
    return 'Bạn không có quyền truy cập Server này.';
  }

  const status = err.response?.status;
  const data = err.response?.data;
  const message = String(data?.detail || data?.message || data?.errorKey || '').toLowerCase();

  if (status === 401 || data?.errorKey === 'notloggedin') {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!';
  }

  if (status === 403 || message.includes('forbidden') || message.includes('không có quyền')) {
    return 'Bạn không còn là thành viên của Server này hoặc đã bị loại.';
  }

  if (status === 404 || message.includes('servernotfound') || message.includes('không tìm thấy')) {
    return 'Server không tồn tại hoặc đã bị xóa.';
  }

  if (status === 400 && (message.includes('forbidden') || message.includes('permission') || message.includes('quyền'))) {
    return 'Bạn không có quyền truy cập hoặc thực hiện thao tác trong Server này.';
  }

  if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
    return 'Yêu cầu quá thời gian chờ. Vui lòng kiểm tra lại kết nối mạng!';
  }

  if (err.code === 'ERR_NETWORK' || !err.response) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!';
  }

  return 'Bạn không có quyền truy cập Server này.';
}

/**
 * Filters out a removed server from the client-side list of servers.
 *
 * @param {Array<Object>} serverList
 * @param {string | number} removedServerId
 * @returns {Array<Object>}
 */
export function filterActiveServersAfterRemoval(serverList, removedServerId) {
  if (!Array.isArray(serverList)) {
    return [];
  }
  if (!removedServerId) {
    return [...serverList];
  }
  return serverList.filter((s) => String(s.id) !== String(removedServerId));
}

/**
 * Resolves safe fallback redirect destination when a user is removed from their current active server.
 *
 * @param {string | number} currentServerId The server the user was actively browsing
 * @param {string | number} removedServerId The server ID from which the user was removed
 * @param {Array<Object>} remainingServers List of remaining accessible servers
 * @returns {string | null} Target route path to redirect to, or null if no redirect needed
 */
export function resolveRemovalRedirectPath(currentServerId, removedServerId, remainingServers = []) {
  if (String(currentServerId) !== String(removedServerId)) {
    return null;
  }
  if (Array.isArray(remainingServers) && remainingServers.length > 0) {
    return `/servers/${remainingServers[0].id}`;
  }
  return '/servers';
}

