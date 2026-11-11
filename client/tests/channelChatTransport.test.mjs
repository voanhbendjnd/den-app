import test from 'node:test';
import assert from 'node:assert/strict';
import { createChannelChatTransport, CHANNEL_RESULTS } from '../src/utils/channelChatTransport.js';
import { getMessageSendError } from '../src/utils/messageInput.js';
import { sortMessagesChronological, mergeMessagePages } from '../src/utils/channelMessages.js';

function setup(t, options = {}) {
  const subscriptions = [], published = [], messages = [];
  let config, connects = 0, token = 'first', sequence = 0;
  const client = {
    connected: false,
    subscribe(destination, callback) {
      const sub = { destination, callback, unsubscribe() { this.closed = true; } };
      subscriptions.push(sub); return sub;
    },
    publish(frame) { published.push(frame); }, activate() {}, deactivate() {},
  };
  const transport = createChannelChatTransport({
    createClient: value => { config = value; return client; }, getToken: () => token,
    onMessage: message => messages.push(message), onConnected: () => connects++,
    makeRequestId: () => `request-${++sequence}`, ...options,
  });
  t.after(() => transport.stop()); transport.setChannel(5); transport.start();
  const emit = (sub, body) => sub.callback({ body: JSON.stringify(body) });
  return { transport, client, subscriptions, published, messages, emit,
    connect() { config.beforeConnect(); client.connected = true; config.onConnect(); },
    close() { client.connected = false; config.onWebSocketClose(); },
    result(body) { emit(subscriptions.findLast(s => s.destination === CHANNEL_RESULTS), body); },
    changeToken(value) { token = value; }, get connects() { return connects; } };
}

test('queue subscribed first; publish waits for matching persisted result', async t => {
  const s = setup(t);
  assert.equal(s.transport.send(5, { content: 'fallback' }), null);
  s.connect(); assert.equal(s.subscriptions[0].destination, CHANNEL_RESULTS);
  const promise = s.transport.send(5, { content: 'Xin chào' });
  let settled = false; promise.then(() => { settled = true; });
  assert.deepEqual(s.published[0], { destination: '/app/channels/5/send', headers: { 'request-id': 'request-1' }, body: JSON.stringify({ content: 'Xin chào' }) });
  s.result({ requestId: 'wrong', channelId: 5, statusCode: 201 });
  s.result({ requestId: 'request-1', channelId: 6, statusCode: 201 });
  s.result({ requestId: 'request-1', channelId: 5, statusCode: 201, message: {} });
  await Promise.resolve(); assert.equal(settled, false);
  const saved = { id: 10, channelId: '5', content: 'Xin chào' };
  s.result({ requestId: 'request-1', channelId: 5, statusCode: 201, message: saved });
  assert.deepEqual(await promise, saved);
});
for (const status of [400, 401, 403, 404, 500]) {
  test(`backend ${status} rejects send without retry`, async t => {
    const s = setup(t); s.connect();
    const promise = s.transport.send(5, { content: 'Draft' });
    s.result({ requestId: 'request-1', channelId: null, statusCode: status });
    await assert.rejects(promise, error => error.response.status === status);
    assert.equal(s.published.length, 1);
  });
}
test('timeout reports uncertainty and never retries', async t => {
  const s = setup(t, { timeoutMs: 5 }); s.connect();
  await assert.rejects(s.transport.send(5, { content: 'Draft' }), error => {
    assert.match(getMessageSendError(error), /kiểm tra lịch sử/); return error.deliveryUncertain;
  });
  assert.equal(s.published.length, 1);
});
test('disconnect rejects pending; reconnect refreshes token and ignores old frames', async t => {
  const s = setup(t); s.connect();
  const oldTopic = s.subscriptions[1], oldQueue = s.subscriptions[0];
  const pending = s.transport.send(5, { content: 'Old' }); s.close();
  await assert.rejects(pending, error => error.deliveryUncertain);
  s.changeToken('new'); s.connect();
  assert.equal(s.client.connectHeaders.Authorization, 'Bearer new');
  assert.equal(s.connects, 2); assert.equal(s.subscriptions.length, 4);
  s.emit(oldTopic, { id: 1, channelId: 5 });
  const fresh = s.transport.send(5, { content: 'New' });
  s.emit(oldQueue, { requestId: 'request-2', channelId: 5, statusCode: 201, message: { id: 2, channelId: 5 } });
  s.result({ requestId: 'request-2', channelId: 5, statusCode: 201, message: { id: 3, channelId: 5 } });
  assert.equal((await fresh).id, 3); assert.deepEqual(s.messages, []);
  assert.equal(s.published.length, 2);
});
test('switch and unmount discard stale topic and reject pending', async t => {
  const s = setup(t); s.connect(); const oldTopic = s.subscriptions[1];
  const pending = s.transport.send(5, { content: 'Old' }); s.transport.setChannel(6);
  await assert.rejects(pending, error => error.deliveryUncertain);
  assert.equal(oldTopic.closed, true); s.emit(oldTopic, { id: 1, channelId: 5 });
  const topic = s.subscriptions.at(-1);
  s.emit(topic, { id: 2, channelId: 5 }); s.emit(topic, { id: 3, channelId: '6' });
  assert.deepEqual(s.messages, [{ id: 3, channelId: '6' }]);
  const current = s.transport.send(6, { content: 'Current' }); s.transport.stop();
  await assert.rejects(current, error => error.deliveryUncertain);
  s.emit(topic, { id: 4, channelId: 6 }); assert.equal(s.messages.length, 1);
});
test('publish failure rejects with uncertainty', async t => {
  const s = setup(t); s.connect(); s.client.publish = () => { throw new Error('Closed'); };
  await assert.rejects(s.transport.send(5, { content: 'Draft' }), error => error.deliveryUncertain);
});
test('history, REST result and broadcast merge once across numeric/string IDs', () => {
  const live = [{ id: 12, content: 'live' }, { id: '11', content: 'saved' }];
  const merged = mergeMessagePages(live, [{ id: 10, content: 'history' }, { id: 11, content: 'history' }]);
  assert.deepEqual(merged.map(m => m.content), ['history', 'saved', 'live']);
  assert.equal(sortMessagesChronological([...merged, { id: '12', content: 'live' }]).length, 3);
});
