import { useCallback, useEffect, useRef } from 'react';
import { shouldNotifyChannel } from '../utils/channelNotifications';
export function useChannelNotification(channelId, userId) {
  const muted = useRef(true);
  const seen = useRef(new Set());
  const count = useRef(0);
  const channel = useRef(channelId);
  channel.current = channelId;
  useEffect(() => {
    muted.current = true; seen.current.clear(); count.current = 0;
    const original = document.title;
    const reset = () => { count.current = 0; document.title = original; };
    window.addEventListener('focus', reset);
    return () => { reset(); window.removeEventListener('focus', reset); };
  }, [channelId]);
  const setMuted = useCallback(value => { muted.current = value; }, []);
  const notify = useCallback(message => {
    if (String(message.channelId) !== String(channel.current) || message.eventType || message.updatedAt || message.id == null) return;
    const id = String(message.id);
    if (seen.current.has(id)) return;
    seen.current.add(id);
    if (!shouldNotifyChannel(message, { channelId: channel.current, userId, muted: muted.current, focused: document.hasFocus() })) return;
    document.title = `(${++count.current}) Tin mới — DenHub`;
    try {
      const context = new (window.AudioContext || window.webkitAudioContext)();
      const oscillator = context.createOscillator(), gain = context.createGain();
      gain.gain.value = 0.08; oscillator.connect(gain); gain.connect(context.destination);
      oscillator.start(); oscillator.stop(context.currentTime + 0.12); oscillator.onended = () => context.close();
    } catch { /* Browsers may block audio until user interaction. */ }
  }, [userId]);
  return { setMuted, notify };
}
