import { Client } from '@stomp/stompjs';
import { getAccessToken } from '../utils/token.js';

const WS_URL = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_WS_URL) || 'ws://localhost:8080/ws';

// STOMP destination constants — change here if backend contract changes
export const DESTINATIONS = {
  subscribeRoom: (roomId) => `/topic/rooms/${roomId}`,
  publishMessage: (roomId) => `/app/chat.send/${roomId}`,
  subscribeChannel: (channelId) => `/topic/channels/${channelId}`,
  subscribeChannelMessages: (channelId) => `/topic/channels/${channelId}/messages`,
  publishChannelMessage: (channelId) => `/app/channels/${channelId}/send`,
};

let stompClient = null;
let activeSubscription = null;
const subscriptions = new Map();

const webSocketService = {
  connect(onConnected, onDisconnected) {
    if (stompClient && stompClient.connected) {
      if (onConnected) onConnected();
      return;
    }

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
    subscriptions.forEach((sub) => {
      try {
        sub.unsubscribe();
      } catch {
        // ignore cleanup error
      }
    });
    subscriptions.clear();
    if (stompClient) {
      stompClient.deactivate();
      stompClient = null;
    }
  },

  subscribe(destination, callback) {
    if (!stompClient || !stompClient.connected) {
      console.warn('WebSocket not connected. Cannot subscribe to:', destination);
      return () => {};
    }
    this.unsubscribe(); // clean up previous subscription
    activeSubscription = stompClient.subscribe(destination, (message) => {
      try {
        const body = JSON.parse(message.body);
        callback(body);
      } catch {
        callback(message.body);
      }
    });
    const sub = activeSubscription;
    return () => {
      try {
        sub.unsubscribe();
      } catch {
        // ignore
      }
      if (activeSubscription === sub) {
        activeSubscription = null;
      }
    };
  },

  subscribeTopic(destination, callback) {
    if (!stompClient || !stompClient.connected) {
      console.warn('WebSocket not connected. Cannot subscribe to:', destination);
      return () => {};
    }
    if (subscriptions.has(destination)) {
      try {
        subscriptions.get(destination).unsubscribe();
      } catch {
        // ignore
      }
    }
    const sub = stompClient.subscribe(destination, (message) => {
      try {
        const body = JSON.parse(message.body);
        callback(body);
      } catch {
        callback(message.body);
      }
    });
    subscriptions.set(destination, sub);
    return () => {
      try {
        sub.unsubscribe();
      } catch {
        // ignore
      }
      if (subscriptions.get(destination) === sub) {
        subscriptions.delete(destination);
      }
    };
  },

  unsubscribeTopic(destination) {
    if (subscriptions.has(destination)) {
      try {
        subscriptions.get(destination).unsubscribe();
      } catch {
        // ignore
      }
      subscriptions.delete(destination);
    }
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
