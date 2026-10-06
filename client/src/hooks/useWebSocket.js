import { useEffect, useRef, useState } from 'react';
import webSocketService, { DESTINATIONS } from '../services/webSocketService';

/**
 * Manages the WebSocket connection lifecycle for a single room.
 * Connects when the component mounts, disconnects when it unmounts.
 *
 * @param {string|null} roomId - the room to subscribe to
 * @param {function} onMessage - called with each incoming message
 */
export function useWebSocket(roomId, onMessage) {
  const [connected, setConnected] = useState(false);
  const onMessageRef = useRef(onMessage);

  // Keep the callback ref up-to-date without re-running the effect
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    if (!roomId) return;

    webSocketService.connect(
      () => {
        setConnected(true);
        webSocketService.subscribe(
          DESTINATIONS.subscribeRoom(roomId),
          (message) => onMessageRef.current(message)
        );
      },
      () => setConnected(false)
    );

    return () => {
      webSocketService.unsubscribe();
      setConnected(false);
    };
  }, [roomId]);

  const sendMessage = (body) => {
    webSocketService.publish(DESTINATIONS.publishMessage(roomId), body);
  };

  return { connected, sendMessage };
}
