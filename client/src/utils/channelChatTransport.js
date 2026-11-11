import { channelSendDestination, channelTopic, isMessageForChannel } from './channelWebSocket.js';

export const CHANNEL_RESULTS = '/user/queue/channel-message-results';

function uncertainDelivery() {
  return Object.assign(new Error('Delivery not confirmed'), { deliveryUncertain: true });
}

// Test the same connection and acknowledgement lifecycle that the React hook uses.
export function createChannelChatTransport({ createClient, getToken, onMessage, onConnected, onState, onFeature,
  timeoutMs = 15000, makeRequestId = () => globalThis.crypto.randomUUID() }) {
  let channelId = null;
  let topicSubscription;
  let resultSubscription;
  let generation = 0;
  let connection = 0;
  let ready = false;
  let stopped = false;
  const pending = new Map();
  const rejectPending = () => {
    for (const entry of pending.values()) {
      clearTimeout(entry.timer);
      entry.reject(uncertainDelivery());
    }
    pending.clear();
  };
  const disconnect = () => {
    generation += 1;
    connection += 1;
    ready = false;
    topicSubscription = null;
    resultSubscription = null;
    rejectPending();
    if (!stopped) onState?.(false);
  };
  function subscribeChannel() {
    topicSubscription?.unsubscribe();
    topicSubscription = null;
    const version = ++generation;
    const subscribedChannel = channelId;
    if (!client.connected || channelId == null) return;
    topicSubscription = client.subscribe(channelTopic(channelId), (frame) => {
      if (stopped || !ready || version !== generation) return;
      try {
        const message = JSON.parse(frame.body);
        if (isMessageForChannel(message, subscribedChannel)) onMessage?.(message);
      } catch { /* Ignore malformed broker frames. */ }
    });
  }
  const client = createClient({
    beforeConnect: () => {
      const token = getToken();
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    onConnect: () => {
      if (stopped) return;
      const version = ++connection;
      // Queue subscription is sent before any SEND frame.
      resultSubscription = client.subscribe(CHANNEL_RESULTS, (frame) => {
        if (stopped || !ready || version !== connection) return;
        let result;
        try { result = JSON.parse(frame.body); } catch { return; }
        const entry = pending.get(result?.requestId);
        if (!entry || (result.channelId != null && String(result.channelId) !== String(entry.channelId))) return;
        if (result.statusCode === 201 && (result.message?.id == null || !isMessageForChannel(result.message, entry.channelId))) return;
        pending.delete(result.requestId);
        clearTimeout(entry.timer);
        if (result.statusCode === 201) entry.resolve(result.message);
        else entry.reject({ response: { status: result.statusCode } });
      });
      if (onFeature) client.subscribe('/user/queue/channel-features', frame => {
        if (stopped || !ready || version !== connection) return;
        try { onFeature(JSON.parse(frame.body)); } catch { /* Ignore malformed frames. */ }
      });
      subscribeChannel();
      ready = true;
      onState?.(true);
      onConnected?.();
    },
    onWebSocketClose: disconnect,
    onStompError: disconnect,
  });
  return {
    start() { client.activate(); },
    setChannel(id) {
      if (String(id) === String(channelId)) return;
      channelId = id;
      rejectPending();
      subscribeChannel();
    },
    send(id, payload) {
      if (!ready || !client.connected || id == null || String(id) !== String(channelId)) return null;
      return new Promise((resolve, reject) => {
        const requestId = makeRequestId();
        const timer = setTimeout(() => {
          pending.delete(requestId);
          reject(uncertainDelivery());
        }, timeoutMs);
        pending.set(requestId, { resolve, reject, timer, channelId: id });
        try {
          client.publish({ destination: channelSendDestination(id), headers: { 'request-id': requestId }, body: JSON.stringify(payload) });
        } catch {
          pending.delete(requestId);
          clearTimeout(timer);
          reject(uncertainDelivery());
        }
      });
    },
    stop() {
      stopped = true;
      disconnect();
      client.deactivate();
    },
  };
}
