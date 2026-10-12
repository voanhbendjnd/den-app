import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/token';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

/**
 * Hook quản lý WebSocket riêng cho Direct Message (1-1).
 * Tạo STOMP client độc lập (không share với room chat) để tránh conflict.
 *
 * @param {number|null} conversationId - ID conversation cần subscribe
 * @param {function}    onMessage      - callback khi nhận tin nhắn mới
 * @returns {{ connected: boolean, sendDM: function }}
 */
export function useDirectChat(conversationId, onMessage) {
  const [connected, setConnected] = useState(false);
  const stompRef = useRef(null);
  const subscriptionRef = useRef(null);
  const onMessageRef = useRef(onMessage);

  const conversationIdRef = useRef(conversationId);
  conversationIdRef.current = conversationId;

  // Keep callback ref fresh without re-running effects
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  function subscribeToConversation(convId) {
    if (!stompRef.current?.connected || !convId) return;
    // Hủy subscription cũ
    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }
    const topic = `/topic/conversations/${convId}`;
    subscriptionRef.current = stompRef.current.subscribe(topic, (frame) => {
      try {
        const message = JSON.parse(frame.body);
        onMessageRef.current?.(message);
      } catch (err) {
        console.error('[DM WebSocket] Failed to parse message:', err);
      }
    });
  }

  // Khởi tạo STOMP client độc lập ngay khi mount (kể cả khi chưa có conversationId)
  useEffect(() => {
    const token = getAccessToken();
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        setConnected(true);
        if (conversationIdRef.current) {
          subscribeToConversation(conversationIdRef.current);
        }
      },
      onDisconnect: () => {
        setConnected(false);
      },
      onStompError: (frame) => {
        console.error('[DM WebSocket] STOMP error:', frame);
      },
    });

    stompRef.current = client;
    client.activate();

    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
        subscriptionRef.current = null;
      }
      if (stompRef.current) {
        stompRef.current.deactivate();
        stompRef.current = null;
      }
      setConnected(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Khi conversationId thay đổi, re-subscribe topic mới
  useEffect(() => {
    if (!stompRef.current?.connected) return;
    if (conversationId) {
      subscribeToConversation(conversationId);
    } else if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId]);

  /**
   * Gửi tin nhắn 1-1 qua STOMP.
   * @param {{ conversationId?, targetUserId?, type?, content?, mediaUrl?, mediaUrls? }} payload
   */
  const sendDM = useCallback((payload) => {
    if (!stompRef.current?.connected) {
      console.warn('[DM WebSocket] Not connected, cannot send message');
      return;
    }
    stompRef.current.publish({
      destination: '/app/conversations/send',
      body: JSON.stringify(payload),
    });
  }, []);

  return { connected, sendDM };
}
