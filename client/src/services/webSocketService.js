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
const connectionListeners = new Set();

const webSocketService = {
  connect(onConnected, onDisconnected) {
    if (onConnected) this.addConnectionListener(onConnected);

    if (stompClient && stompClient.connected) {
      if (onConnected) {
        try {
          onConnected(true);
        } catch {
          // ignore callback error
        }
      }
      return;
    }

    if (stompClient && stompClient.active) {
      // Already activating or connecting
      return;
    }

    stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
      onConnect: () => {
        connectionListeners.forEach((fn) => {
          try {
            fn(true);
          } catch {
            // ignore
          }
        });
      },
      onDisconnect: () => {
        if (onDisconnected) {
          try {
            onDisconnected();
          } catch {
            // ignore
          }
        }
        connectionListeners.forEach((fn) => {
          try {
            fn(false);
          } catch {
            // ignore
          }
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      },
    });

    stompClient.activate();
  },

  addConnectionListener(callback) {
    if (typeof callback !== 'function') return () => {};
    connectionListeners.add(callback);
    if (this.isConnected()) {
      try {
        callback(true);
      } catch {
        // ignore
      }
    }
    return () => {
      connectionListeners.delete(callback);
    };
  },

  removeConnectionListener(callback) {
    connectionListeners.delete(callback);
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
    connectionListeners.clear();
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

  subscribeChannel(channelId, callback) {
    if (channelId == null || channelId === '') return () => {};
    return this.subscribeTopic(DESTINATIONS.subscribeChannel(channelId), callback);
  },

  unsubscribeChannel(channelId) {
    if (channelId == null || channelId === '') return;
    this.unsubscribeTopic(DESTINATIONS.subscribeChannel(channelId));
  },

  publishChannelMessage(channelId, body) {
    if (channelId == null || channelId === '') return;
    this.publish(DESTINATIONS.publishChannelMessage(channelId), body);
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
