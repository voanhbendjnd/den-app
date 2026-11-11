export function shouldNotifyChannel(message, { channelId, userId, muted, focused }) {
  return message?.id != null && String(message.channelId) === String(channelId)
    && String(message.senderId) !== String(userId) && !message.eventType
    && !message.updatedAt && !muted && !focused;
}
