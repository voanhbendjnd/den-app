import test from 'node:test';
import assert from 'node:assert/strict';

import {
  resolveActiveChannel,
  getChannelTopic,
  normalizeRealtimeMessage,
  isMessageForActiveChannel,
  handleIncomingRealtimeMessage,
  sortMessagesChronological,
} from '../src/utils/channelMessages.js';
import webSocketService, { DESTINATIONS } from '../src/services/webSocketService.js';

test('resolveActiveChannel handles empty and invalid channel inputs safely', () => {
  assert.equal(resolveActiveChannel([]), null);
  assert.equal(resolveActiveChannel(null), null);
  assert.equal(resolveActiveChannel(undefined), null);
  assert.equal(resolveActiveChannel('invalid'), null);
});

test('resolveActiveChannel prioritizes preferredChannelId (from route or URL param)', () => {
  const channels = [
    { id: 1, name: 'general' },
    { id: 2, name: 'random' },
    { id: 3, name: 'announcements' },
  ];

  // Matches by number
  assert.deepEqual(resolveActiveChannel(channels, 1, 2), channels[1]);
  // Matches by string ID
  assert.deepEqual(resolveActiveChannel(channels, 1, '3'), channels[2]);
});

test('resolveActiveChannel falls back to currentActiveId when preferred is absent or invalid', () => {
  const channels = [
    { id: 10, name: 'general' },
    { id: 20, name: 'dev-chat' },
  ];

  // Preferred is null
  assert.deepEqual(resolveActiveChannel(channels, 20, null), channels[1]);
  // Preferred does not exist in channels list
  assert.deepEqual(resolveActiveChannel(channels, 20, 999), channels[1]);
  // String ID matching
  assert.deepEqual(resolveActiveChannel(channels, '20', undefined), channels[1]);
});

test('resolveActiveChannel prioritizes general channel when neither preferred nor current is available', () => {
  const channels = [
    { id: 101, name: 'random' },
    { id: 102, name: 'General' },
    { id: 103, name: 'help' },
  ];

  assert.deepEqual(resolveActiveChannel(channels, null, null), channels[1]);
  assert.deepEqual(resolveActiveChannel(channels, 999, 888), channels[1]);
});

test('resolveActiveChannel falls back to first channel when no general channel exists', () => {
  const channels = [
    { id: 201, name: 'lobby' },
    { id: 202, name: 'qa' },
  ];

  assert.deepEqual(resolveActiveChannel(channels, null, null), channels[0]);
});

test('getChannelTopic constructs correct STOMP destination strings', () => {
  assert.equal(getChannelTopic(42), '/topic/channels/42');
  assert.equal(getChannelTopic('99'), '/topic/channels/99');
  assert.equal(getChannelTopic(null), '');
  assert.equal(getChannelTopic(undefined), '');
  assert.equal(getChannelTopic(''), '');
});

test('DESTINATIONS provides confirmed channel destinations (3.01)', () => {
  assert.equal(DESTINATIONS.subscribeChannel(10), '/topic/channels/10');
  assert.equal(DESTINATIONS.subscribeChannelMessages(10), '/topic/channels/10/messages');
  assert.equal(DESTINATIONS.publishChannelMessage(10), '/app/channels/10/send');
});

test('normalizeRealtimeMessage unwraps message payloads properly', () => {
  // Direct message object
  const direct = { id: 1, content: 'Hello direct', channelId: 10 };
  assert.deepEqual(normalizeRealtimeMessage(direct), direct);

  // Wrapped: { type: 'MESSAGE_CREATED', data: message }
  const wrappedType = { type: 'MESSAGE_CREATED', data: { id: 2, content: 'Wrapped data', channelId: 10 } };
  assert.deepEqual(normalizeRealtimeMessage(wrappedType), wrappedType.data);

  // Wrapped: { payload: message }
  const wrappedPayload = { event: 'send', payload: { id: 3, content: 'Wrapped payload', channelId: 10 } };
  assert.deepEqual(normalizeRealtimeMessage(wrappedPayload), wrappedPayload.payload);

  // Invalid or null
  assert.equal(normalizeRealtimeMessage(null), null);
  assert.equal(normalizeRealtimeMessage(undefined), null);
  assert.equal(normalizeRealtimeMessage({ notAMessage: true }), null);
});

test('isMessageForActiveChannel validates matching channel and rejects other channels (3.06)', () => {
  const activeChannelId = 101;

  // Matching numeric and string channelId
  assert.equal(isMessageForActiveChannel({ id: 1, channelId: 101, content: 'hi' }, activeChannelId), true);
  assert.equal(isMessageForActiveChannel({ id: 2, channelId: '101', content: 'hi' }, activeChannelId), true);

  // Mismatched channelId (from previous channel or another channel) -> MUST RETURN FALSE
  assert.equal(isMessageForActiveChannel({ id: 3, channelId: 999, content: 'old channel msg' }, activeChannelId), false);
  assert.equal(isMessageForActiveChannel({ id: 4, channelId: '102', content: 'other channel' }, activeChannelId), false);

  // Wrapped format from WebSocket broker
  const wrappedMatch = { type: 'MESSAGE_CREATED', data: { id: 5, channelId: 101, content: 'wrapped match' } };
  assert.equal(isMessageForActiveChannel(wrappedMatch, activeChannelId), true);

  const wrappedOld = { type: 'MESSAGE_CREATED', data: { id: 6, channelId: 999, content: 'wrapped old' } };
  assert.equal(isMessageForActiveChannel(wrappedOld, activeChannelId), false);

  // Invalid inputs
  assert.equal(isMessageForActiveChannel(null, activeChannelId), false);
  assert.equal(isMessageForActiveChannel({ id: 7 }, null), false);
});

test('handleIncomingRealtimeMessage strictly ignores messages belonging to other channels (3.06)', () => {
  const current = [
    { id: 1, content: 'msg 1', channelId: 10, createdAt: '2026-10-30T10:00:00Z' },
  ];
  const foreignMessage = { id: 2, content: 'msg from channel 99', channelId: 99, createdAt: '2026-10-30T10:01:00Z' };

  const result = handleIncomingRealtimeMessage(current, foreignMessage, 10);
  assert.equal(result.length, 1);
  assert.equal(result[0].id, 1);
  assert.equal(result, current); // Preserves exact array reference when ignored
});

test('channel switching flow ensures complete cross-channel isolation (3.06)', () => {
  // Step 1: User is on Channel A (id: 101)
  let channelAMessages = [
    { id: 1, channelId: 101, content: 'Message in channel A', createdAt: '2026-10-30T10:00:00Z' },
  ];

  // Step 2: User switches to Channel B (id: 102) -> New state initialized
  let channelBMessages = [];

  // Step 3: Delayed message from Channel A arrives via WebSocket
  const delayedMessageA = { id: 2, channelId: 101, content: 'Late message from Channel A', createdAt: '2026-10-30T10:01:00Z' };

  // Attempting to inject delayed message A into Channel B MUST be rejected
  const afterLateA = handleIncomingRealtimeMessage(channelBMessages, delayedMessageA, 102);
  assert.equal(afterLateA.length, 0); // No message added!

  // Step 4: Real-time message for Channel B arrives
  const messageB = { id: 3, channelId: 102, content: 'First message in Channel B', createdAt: '2026-10-30T10:02:00Z' };
  channelBMessages = handleIncomingRealtimeMessage(channelBMessages, messageB, 102);

  assert.equal(channelBMessages.length, 1);
  assert.equal(channelBMessages[0].id, 3);
  assert.equal(channelBMessages[0].content, 'First message in Channel B');
});

test('handleIncomingRealtimeMessage appends and orders messages for active channel (3.01)', () => {
  const current = [
    { id: 1, content: 'msg 1', channelId: 10, createdAt: '2026-10-30T10:00:00Z' },
  ];
  const newMsg = { id: 2, content: 'msg 2', channelId: 10, createdAt: '2026-10-30T10:05:00Z' };

  const result = handleIncomingRealtimeMessage(current, newMsg, 10);
  assert.equal(result.length, 2);
  assert.equal(result[1].id, 2);
  assert.equal(result[1].content, 'msg 2');
});

test('handleIncomingRealtimeMessage deduplicates existing identical messages', () => {
  const current = [
    { id: 1, content: 'msg 1', channelId: 10, createdAt: '2026-10-30T10:00:00Z' },
  ];
  const duplicate = { id: 1, content: 'msg 1', channelId: 10, createdAt: '2026-10-30T10:00:00Z' };

  const result = handleIncomingRealtimeMessage(current, duplicate, 10);
  assert.equal(result.length, 1);
  assert.equal(result, current); // unchanged reference when duplicate
});

test('handleIncomingRealtimeMessage updates edited messages with same id', () => {
  const current = [
    { id: 1, content: 'old content', channelId: 10, createdAt: '2026-10-30T10:00:00Z', updatedAt: '2026-10-30T10:00:00Z' },
  ];
  const edited = { id: 1, content: 'updated content', channelId: 10, createdAt: '2026-10-30T10:00:00Z', updatedAt: '2026-10-30T10:10:00Z' };

  const result = handleIncomingRealtimeMessage(current, edited, 10);
  assert.equal(result.length, 1);
  assert.equal(result[0].content, 'updated content');
});

test('webSocketService channel helper methods operate safely (3.01 & 3.06)', () => {
  // Safe calls when disconnected
  assert.doesNotThrow(() => {
    const unsub = webSocketService.subscribeChannel(null, () => {});
    assert.equal(typeof unsub, 'function');
    unsub();
  });

  assert.doesNotThrow(() => {
    webSocketService.unsubscribeChannel(null);
    webSocketService.publishChannelMessage(null, {});
  });

  // Connection listener management
  let called = false;
  const listener = (status) => { called = true; };
  const remove = webSocketService.addConnectionListener(listener);
  assert.equal(typeof remove, 'function');
  remove();
});
