import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/token';
import { channelSendDestination, channelTopic } from '../utils/channelWebSocket';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

/**
 * STOMP cho chat nhóm (Channel).
 * Subscribe `/topic/channels/{channelId}` và gửi tới `/app/channels/{channelId}/send`.
 *
 * @param {number|string|null} channelId
 * @param {function} onMessage - callback khi nhận tin nhắn mới (MessageResponse)
 * @returns {{ connected: boolean, sendChannelMessage: (channelId, payload) => boolean }}
 */
export function useChannelChat(channelId, onMessage) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const onMessageRef = useRef(onMessage);
  const channelIdRef = useRef(channelId);
  channelIdRef.current = channelId;

  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  const subscribe = useCallback((id) => {
    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }
    if (!clientRef.current?.connected || id == null) return;
    subscriptionRef.current = clientRef.current.subscribe(channelTopic(id), (frame) => {
      try {
        onMessageRef.current?.(JSON.parse(frame.body));
      } catch (err) {
        console.error('[Channel WebSocket] Failed to parse message:', err);
      }
    });
  }, []);

  useEffect(() => {
    const token = getAccessToken();
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        setConnected(true);
        subscribe(channelIdRef.current);
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => console.error('[Channel WebSocket] STOMP error:', frame),
    });
    clientRef.current = client;
    client.activate();
    return () => {
      subscriptionRef.current?.unsubscribe();
      subscriptionRef.current = null;
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [subscribe]);

  useEffect(() => {
    subscribe(channelId);
  }, [channelId, connected, subscribe]);

  const sendChannelMessage = useCallback((id, payload) => {
    if (!clientRef.current?.connected || id == null) return false;
    clientRef.current.publish({
      destination: channelSendDestination(id),
      body: JSON.stringify(payload),
    });
    return true;
  }, []);

  return { connected, sendChannelMessage };
}
