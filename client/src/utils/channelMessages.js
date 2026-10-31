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
    ? members.find((m) => m && (String(m.id) === String(senderId) || String(m.userId) === String(senderId)))
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
 * Resolves which channel should be active given the available channels list,
 * the currently active channel ID, and an optional preferred channel ID (e.g., from URL params).
 * Priority:
 * 1. preferredChannelId (matching channel in list)
 * 2. currentActiveId (if still exists in list)
 * 3. Channel named 'general' (case-insensitive)
 * 4. First channel in list
 * 5. null if list is empty
 */
export function resolveActiveChannel(channels = [], currentActiveId = null, preferredChannelId = null) {
  if (!Array.isArray(channels) || channels.length === 0) return null;

  if (preferredChannelId != null) {
    const matchPreferred = channels.find((ch) => ch && String(ch.id) === String(preferredChannelId));
    if (matchPreferred) return matchPreferred;
  }

  if (currentActiveId != null) {
    const matchCurrent = channels.find((ch) => ch && String(ch.id) === String(currentActiveId));
    if (matchCurrent) return matchCurrent;
  }

  const general = channels.find((ch) => ch && ch.name && ch.name.toLowerCase() === 'general');
  if (general) return general;

  return channels[0] || null;
}

/**
 * Builds the STOMP destination topic for a channel.
 */
export function getChannelTopic(channelId) {
  if (channelId == null || channelId === '') return '';
  return `/topic/channels/${channelId}`;
}

/**
 * Normalizes incoming WebSocket message payloads.
 * Unwraps `{ type, data }`, `{ event, payload }` or returns raw message object.
 */
export function normalizeRealtimeMessage(payload) {
  if (!payload || typeof payload !== 'object') return null;

  // Wrapped format: { type: 'MESSAGE_CREATED', data: { ... } }
  if (payload.data && typeof payload.data === 'object' && payload.data.id != null) {
    return payload.data;
  }

  // Wrapped format: { payload: { ... } }
  if (payload.payload && typeof payload.payload === 'object' && payload.payload.id != null) {
    return payload.payload;
  }

  // Direct message object
  if (payload.id != null) {
    return payload;
  }

  return null;
}

/**
 * Verifies whether an incoming message belongs to the active channel.
 * Returns false if incoming message belongs to another channel, preventing cross-channel message leakage.
 */
export function isMessageForActiveChannel(incoming, activeChannelId) {
  if (incoming == null || activeChannelId == null) return false;
  const normalized = normalizeRealtimeMessage(incoming);
  if (!normalized) return false;
  if (normalized.channelId == null) return true;
  return String(normalized.channelId) === String(activeChannelId);
}

/**
 * Handles incoming real-time message by verifying it belongs to activeChannelId,
 * preventing duplicates, and maintaining chronological order.
 */
export function handleIncomingRealtimeMessage(currentMessages = [], incoming = null, activeChannelId = null) {
  const current = Array.isArray(currentMessages) ? currentMessages : [];
  if (!isMessageForActiveChannel(incoming, activeChannelId)) return current;
  const normalized = normalizeRealtimeMessage(incoming);
  if (!normalized) return current;

  // Check if message is already in list
  const existingIndex = current.findIndex((m) => m && String(m.id) === String(normalized.id));
  if (existingIndex >= 0) {
    // If identical, return existing array unchanged
    const existing = current[existingIndex];
    if (existing.content === normalized.content && existing.updatedAt === normalized.updatedAt) {
      return current;
    }
    const updated = [...current];
    updated[existingIndex] = { ...existing, ...normalized };
    return updated;
  }

  // Append new message and ensure order
  return sortMessagesChronological([...current, normalized]);
}
