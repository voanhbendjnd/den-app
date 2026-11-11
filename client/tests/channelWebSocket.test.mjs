import test from 'node:test';
import assert from 'node:assert/strict';
import { channelSendDestination, channelTopic, isMessageForChannel } from '../src/utils/channelWebSocket.js';

test('destination khớp contract docs/02 mục 6', () => {
  assert.equal(channelTopic(5), '/topic/channels/5');
  assert.equal(channelSendDestination(5), '/app/channels/5/send');
});

test('chỉ nhận tin đúng kênh đang mở', () => {
  assert.equal(isMessageForChannel({ channelId: 5 }, 5), true);
  assert.equal(isMessageForChannel({ channelId: '5' }, 5), true);
  assert.equal(isMessageForChannel({ channelId: 6 }, 5), false);
  assert.equal(isMessageForChannel({ channelId: 5 }, null), false);
  assert.equal(isMessageForChannel(null, 5), false);
});
