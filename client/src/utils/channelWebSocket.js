// Destination STOMP cho chat nhóm (docs/02-SYSTEM-ARCHITECTURE.md mục 6).
export const channelTopic = (channelId) => `/topic/channels/${channelId}`;
export const channelSendDestination = (channelId) => `/app/channels/${channelId}/send`;

// Chỉ nhận tin thuộc kênh đang mở.
export function isMessageForChannel(message, activeChannelId) {
  return message?.channelId != null && activeChannelId != null
    && String(message.channelId) === String(activeChannelId);
}
