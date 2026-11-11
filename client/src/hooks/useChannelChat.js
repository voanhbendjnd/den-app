import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/token';
import { createChannelChatTransport } from '../utils/channelChatTransport';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

export function useChannelChat(channelId, onMessage, onConnected) {
  const [connected, setConnected] = useState(false);
  const transportRef = useRef(null);
  const callbacks = useRef({ onMessage, onConnected });
  callbacks.current = { onMessage, onConnected };
  const channelRef = useRef(channelId);
  channelRef.current = channelId;

  useEffect(() => {
    const transport = createChannelChatTransport({
      createClient: (options) => new Client({ brokerURL: WS_URL, reconnectDelay: 5000, ...options }),
      getToken: getAccessToken,
      onMessage: (message) => callbacks.current.onMessage?.(message),
      onConnected: () => callbacks.current.onConnected?.(),
      onState: setConnected,
    });
    transportRef.current = transport;
    transport.setChannel(channelRef.current);
    transport.start();
    return () => {
      transportRef.current = null;
      transport.stop();
    };
  }, []);

  useEffect(() => { transportRef.current?.setChannel(channelId); }, [channelId]);

  // null means no frame was sent: the caller may safely use REST.
  const sendChannelMessage = useCallback((id, payload) => transportRef.current?.send(id, payload) ?? null, []);
  return { connected, sendChannelMessage };
}
