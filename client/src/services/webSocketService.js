import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/token';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

// STOMP destination constants — change here if backend contract changes
export const DESTINATIONS = {
  subscribeRoom: (roomId) => `/topic/rooms/${roomId}`,
  publishMessage: (roomId) => `/app/chat.send/${roomId}`,
};

let stompClient = null;
let activeSubscription = null;

const webSocketService = {
  connect(onConnected, onDisconnected) {
    if (stompClient && stompClient.connected) return;

    stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
      onConnect: () => {
        if (onConnected) onConnected();
      },
      onDisconnect: () => {
        if (onDisconnected) onDisconnected();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      },
    });

    stompClient.activate();
  },

  disconnect() {
    this.unsubscribe();
    if (stompClient) {
      stompClient.deactivate();
      stompClient = null;
    }
  },

  subscribe(destination, callback) {
    if (!stompClient || !stompClient.connected) {
      console.warn('WebSocket not connected. Cannot subscribe to:', destination);
      return;
    }
    this.unsubscribe(); // clean up previous subscription
    activeSubscription = stompClient.subscribe(destination, (message) => {
      const body = JSON.parse(message.body);
      callback(body);
    });
  },

  unsubscribe() {
    if (activeSubscription) {
      activeSubscription.unsubscribe();
      activeSubscription = null;
    }
  },

  publish(destination, body) {
    if (!stompClient || !stompClient.connected) {
      console.warn('WebSocket not connected. Cannot publish to:', destination);
      return;
    }
    stompClient.publish({
      destination,
      body: JSON.stringify(body),
    });
  },

  isConnected() {
    return stompClient?.connected || false;
  },
};

export default webSocketService;
