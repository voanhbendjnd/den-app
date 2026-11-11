import { useEffect, useRef, useCallback } from 'react';

const DEFAULT_TITLE = 'DenHub';

/**
 * Tạo âm thanh ping nhẹ bằng Web Audio API (không cần file audio).
 */
function playPing() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();

    const osc = ctx.createOscillator();
    const gainNode = ctx.createGain();

    osc.connect(gainNode);
    gainNode.connect(ctx.destination);

    osc.type = 'sine';
    osc.frequency.setValueAtTime(880, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(660, ctx.currentTime + 0.12);

    gainNode.gain.setValueAtTime(0.18, ctx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);

    osc.start(ctx.currentTime);
    osc.stop(ctx.currentTime + 0.35);

    osc.onended = () => ctx.close();
  } catch {
    // Bỏ qua nếu browser không hỗ trợ hoặc autoplay bị block
  }
}

/**
 * Hook quản lý thông báo khi có tin nhắn DM mới.
 *
 * @param {number|string|null} currentUserId
 * @returns {{ notifyIncoming: function }}
 */
export function useNotification(currentUserId) {
  const unreadCountRef = useRef(0);

  // Reset title & count khi tab được focus lại
  useEffect(() => {
    function handleFocus() {
      unreadCountRef.current = 0;
      document.title = DEFAULT_TITLE;
    }
    window.addEventListener('focus', handleFocus);
    return () => window.removeEventListener('focus', handleFocus);
  }, []);

  // Reset title về mặc định khi unmount
  useEffect(() => {
    return () => {
      document.title = DEFAULT_TITLE;
    };
  }, []);

  /**
   * Gọi khi có tin nhắn mới đến.
   * Chỉ phát thông báo nếu tin đến từ người khác.
   *
   * @param {object} msg               - MessageHistoryDTO từ WebSocket
   * @param {string} [senderDisplayName]
   */
  const notifyIncoming = useCallback((msg, senderDisplayName) => {
    const senderId = msg?.sender?.userId ?? msg?.senderId;
    const isOwn = String(senderId) === String(currentUserId);
    if (isOwn) return;

    // Phát âm thanh ping
    playPing();

    // Cập nhật tiêu đề tab khi tab không được focus
    if (!document.hasFocus()) {
      unreadCountRef.current += 1;
      const name = senderDisplayName
        || msg?.sender?.name
        || msg?.sender?.username
        || 'Ai đó';
      document.title = `(${unreadCountRef.current}) ${name} — DenHub`;
    }
  }, [currentUserId]);

  return { notifyIncoming };
}
