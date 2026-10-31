import dayjs from 'dayjs';

/**
 * Sorts messages in chronological order (oldest first, newest last)
 * and deduplicates messages by id.
 */
export function sortMessagesChronological(items) {
  if (!Array.isArray(items)) return [];
  const map = new Map();
  for (const item of items) {
    if (item && item.id != null) {
      map.set(item.id, item);
    }
  }
  return Array.from(map.values()).sort((a, b) => {
    if (a.id !== b.id) return a.id - b.id;
    return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
  });
}

/**
 * Merges older message page into existing message list,
 * keeping chronological order and removing duplicates.
 */
export function mergeMessagePages(existingMessages, olderItems) {
  const existing = Array.isArray(existingMessages) ? existingMessages : [];
  const older = Array.isArray(olderItems) ? olderItems : [];
  return sortMessagesChronological([...older, ...existing]);
}

/**
 * Formats a message timestamp into a friendly human-readable format.
 * E.g. "Hôm nay lúc 14:30", "Hôm qua lúc 09:15", or "DD/MM/YYYY HH:mm"
 */
export function formatMessageTime(isoString) {
  if (!isoString) return '';
  const date = dayjs(isoString);
  if (!date.isValid()) return '';

  const now = dayjs();
  if (date.isSame(now, 'day')) {
    return `Hôm nay lúc ${date.format('HH:mm')}`;
  }
  if (date.isSame(now.subtract(1, 'day'), 'day')) {
    return `Hôm qua lúc ${date.format('HH:mm')}`;
  }
  return date.format('DD/MM/YYYY HH:mm');
}

/**
 * Compact time for grouped messages (HH:mm).
 */
export function formatCompactTime(isoString) {
  if (!isoString) return '';
  const date = dayjs(isoString);
  return date.isValid() ? date.format('HH:mm') : '';
}

/**
 * Resolves author details for a message.
 */
export function getAuthorInfo(senderId, members = [], currentUser = null, serverOwnerId = null) {
  const isOwn = currentUser?.id != null && String(senderId) === String(currentUser.id);
  const isOwner = serverOwnerId != null && String(senderId) === String(serverOwnerId);

  const member = Array.isArray(members)
    ? members.find((m) => m && String(m.id) === String(senderId))
    : null;

  let name = 'Thành viên';
  let avatar = null;

  if (isOwn) {
    name = currentUser.displayName || currentUser.username || currentUser.name || 'Bạn';
    avatar = currentUser.avatar || null;
  } else if (member) {
    name = member.nickname || member.name || member.username || `Thành viên #${senderId}`;
    avatar = member.avatar || null;
  } else if (senderId != null) {
    name = `Thành viên #${senderId}`;
  }

  const initial = (name[0] || 'U').toUpperCase();

  return {
    name,
    avatar,
    initial,
    isOwn,
    isOwner,
  };
}

/**
 * Updates a specific message in the list with new data.
 */
export function updateMessageInList(messages, updatedMessage) {
  if (!Array.isArray(messages) || !updatedMessage || updatedMessage.id == null) {
    return Array.isArray(messages) ? messages : [];
  }
  return messages.map((msg) =>
    msg && msg.id === updatedMessage.id ? { ...msg, ...updatedMessage } : msg
  );
}

/**
 * Checks whether a message has been edited.
 */
export function isMessageEdited(message) {
  if (!message) return false;
  return Boolean(message.updatedAt || message.lastModifiedDate);
}

/**
 * Removes a specific message from the list by id.
 */
export function removeMessageFromList(messages, messageId) {
  if (!Array.isArray(messages) || messageId == null) {
    return Array.isArray(messages) ? messages : [];
  }
  return messages.filter((msg) => msg && String(msg.id) !== String(messageId));
}

/**
 * Checks if the current user has permission to delete a message.
 * Strictly restricted to original message author only.
 */
export function canDeleteMessage(authorId, currentUserId) {
  if (currentUserId == null || authorId == null) return false;
  return String(authorId) === String(currentUserId);
}

/**
 * Checks if the current user has permission to edit a message.
 * Strictly restricted to original message author only (BR-MSG-02).
 */
export function canEditMessage(authorId, currentUserId) {
  if (currentUserId == null || authorId == null) return false;
  return String(authorId) === String(currentUserId);
}

/**
 * Calculates new scrollTop after prepending older messages to preserve user's reading position.
 */
export function calculateScrollAfterPrepend(prevScrollTop = 0, prevScrollHeight = 0, newScrollHeight = 0) {
  return Number(prevScrollTop || 0) + (Number(newScrollHeight || 0) - Number(prevScrollHeight || 0));
}



