import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';

// Use an existing Playwright installation (local or NODE_PATH). No production dependency.
const { chromium } = createRequire(import.meta.url)('playwright');
const root = fileURLToPath(new URL('../', import.meta.url));
const apiPattern = /^https?:\/\/[^/]+\/api\//;
const fixtureId = '/__group-ui-test__.jsx';
const servers = [{ id: 1, name: 'Nhóm học tập', iconUrl: null }, { id: 12, name: 'DenHub Design', iconUrl: null }];
const fixtureSource = `
import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider, theme } from 'antd';
import MessageInput from '/src/components/chat/MessageInput.jsx';
import '/src/index.css';
import '/src/pages/chat/group-chat.css';
function Fixture() {
  const [channelId, setChannelId] = useState(10);
  const [disabled, setDisabled] = useState(false);
  const [messages, setMessages] = useState([]);
  window.fixture = { ...window.fixture, setChannelId, setDisabled };
  async function onSend(id, request) {
    window.fixture.calls.push({ channelId: id, ...request });
    if (window.fixture.mode === 'pending') {
      return new Promise((resolve, reject) => { window.fixture.resolve = resolve; window.fixture.reject = reject; });
    }
    if (window.fixture.mode === 'failure') throw { response: { status: window.fixture.status } };
    return { id: window.fixture.calls.length, channelId: id, content: request.content };
  }
  return <ConfigProvider theme={{ algorithm: theme.darkAlgorithm }}>
    <MessageInput channelId={channelId} channelName="test-channel" disabled={disabled} onSend={onSend}
      onSent={(message, channelId) => {
        window.fixture.received.push({ message, channelId });
        setMessages(previous => [...previous, message]);
      }} />
    <output aria-label="Tin nhắn đã gửi">{JSON.stringify(messages)}</output>
  </ConfigProvider>;
}
window.fixture = { calls: [], received: [], mode: 'success' };
createRoot(document.getElementById('root')).render(<Fixture />);
`;

let server;
let browser;
let origin;
const contexts = [];

before(async () => {
  server = await createServer({
    root, configFile: false, server: { host: '127.0.0.1', port: 0 },
    plugins: [react(), {
      name: 'group-ui-test-fixture',
      resolveId(id) { if (id === fixtureId) return id; },
      load(id) { if (id === fixtureId) return fixtureSource; },
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (req.url !== '/__group-ui-test__') return next();
          try {
            const html = await vite.transformIndexHtml(req.url, '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script type="module" src="' + fixtureId + '"></script></body></html>');
            res.setHeader('Content-Type', 'text/html');
            res.end(html);
          } catch (error) { next(error); }
        });
      },
    }],
  });
  await server.listen();
  origin = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge', headless: true });
});

after(async () => {
  await Promise.all(contexts.map(context => context.close()));
  await browser?.close();
  await server?.close();
});

async function openPage({ authenticated = true, width = 1440, handler } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
  contexts.push(context);
  context.setDefaultTimeout(12000);
  context.setDefaultNavigationTimeout(30000);
  await context.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  await context.route('https://images.unsplash.com/**', route => route.fulfill({
    contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jrZkAAAAASUVORK5CYII=', 'base64'),
  }));
  // Fixtures never reach a real API and are not included in the app bundle.
  if (authenticated) {
    await context.addInitScript(() => {
      if (sessionStorage.getItem('denhub_test_auth_initialized')) return;
      sessionStorage.setItem('denhub_test_auth_initialized', 'true');
      const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, sub: 'ui-test@example.invalid' }));
      localStorage.setItem('denhub_access_token', `fixture.${payload}.fixture`);
      localStorage.setItem('denhub_user', JSON.stringify({ id: 100, displayName: 'UI Test', email: 'ui-test@example.invalid' }));
    });
  }
  const requests = [];
  await context.route(apiPattern, async route => {
    const request = route.request();
    if (new URL(request.url()).pathname.startsWith('/api/posts/')) {
      return route.fulfill({ json: { data: { result: [], hasMore: false } } });
    }
    requests.push({ method: request.method(), path: new URL(request.url()).pathname });
    if (handler) return handler(route, requests);
    await fulfillServerRequest(route);
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  return { page, requests, errors };
}

async function fulfillServerRequest(route, createdServers = []) {
  const request = route.request();
  assert.equal(request.method(), 'GET');
  const pathname = new URL(request.url()).pathname;
  if (pathname === '/api/v1/conversations') {
    return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: [] } });
  }
  if (pathname === '/api/v1/servers') {
    return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: servers } });
  }
  if (pathname.endsWith('/channels')) {
    const created = createdServers.find(server => String(server.id) === pathname.split('/')[4]);
    const data = created ? [{ id: 9000 + created.id, serverId: created.id, name: 'general', position: 0 }] : [];
    return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data } });
  }
  if (pathname.endsWith('/members')) {
    return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: [] } });
  }
  if (/^\/api\/channels\/\d+\/messages$/.test(pathname)) {
    return route.fulfill({ json: { data: { items: [], hasMore: false, nextCursor: null } } });
  }
  assert.match(pathname, /^\/api\/v1\/servers\/\d+$/);
  const server = [...createdServers, ...servers].find(item => String(item.id) === pathname.split('/').at(-1));
  if (!server) return route.fulfill({ status: 404, contentType: 'application/problem+json', json: { status: 404, title: 'Not Found' } });
  return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: server } });
}

const scopedServers = [
  { id: 101, ownerId: 100, name: 'Scope Alpha', iconUrl: null },
  { id: 102, ownerId: 100, name: 'Scope Beta', iconUrl: null },
];
const scopedChannels = id => [{ id: id * 10, serverId: id, name: id === 101 ? 'alpha-channel' : 'beta-channel', position: 0 }];
const scopedMessage = (id, content) => ({ id, channelId: id, senderId: 300, content, createdAt: '2026-10-26T12:00:00Z' });
function deferred(t) {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  t.after(() => resolve());
  return { promise, resolve };
}
async function openScopedChat({ owner = true, handler } = {}) {
  return openPage({ handler: async route => {
    const pathname = new URL(route.request().url()).pathname;
    if (handler && await handler(route, pathname)) return;
    const data = scopedServers.map(server => ({ ...server, ownerId: owner ? 100 : 200 }));
    if (pathname === '/api/v1/servers') return route.fulfill({ json: { data } });
    if (/^\/api\/v1\/servers\/\d+$/.test(pathname)) return route.fulfill({ json: { data: data.find(server => String(server.id) === pathname.split('/').at(-1)) } });
    if (pathname.endsWith('/channels')) return route.fulfill({ json: { data: scopedChannels(Number(pathname.split('/')[4])) } });
    if (pathname.endsWith('/members')) return route.fulfill({ json: { data: [
      { id: 1, userId: 100, nickname: 'UI Test', role: owner ? 'OWNER' : 'MEMBER' },
      { id: 2, userId: 300, nickname: 'Common Member', role: 'MEMBER' },
    ] } });
    if (pathname.includes('/messages')) {
      const id = Number(pathname.split('/')[3]);
      return route.fulfill({ json: { data: { items: [scopedMessage(id, id === 1010 ? 'Alpha message' : 'Beta message')], hasMore: false, nextCursor: null } } });
    }
    return route.fulfill({ json: { data: [] } });
  } });
}
async function switchScopedServer(page, name) {
  await page.getByRole('link', { name: `Mở nhóm Scope ${name}`, exact: true }).click();
  await page.locator('.group-sidebar__identity strong').filter({ hasText: `Scope ${name}` }).waitFor();
}

for (const oldStatus of [200, 503]) {
  test(`changing channels ignores old history ${oldStatus} without clearing the new channel loading`, async t => {
    const oldGate = deferred(t), newGate = deferred(t);
    const { page, errors } = await openScopedChat({ handler: async (route, pathname) => {
      if (pathname === '/api/v1/servers/101/channels') {
        await route.fulfill({ json: { data: [...scopedChannels(101), { id: 1011, serverId: 101, name: 'second-channel' }] } });
        return true;
      }
      if (pathname !== '/api/channels/1010/messages' && pathname !== '/api/channels/1011/messages') return false;
      const old = pathname.includes('/1010/');
      await (old ? oldGate.promise : newGate.promise);
      await route.fulfill(old && oldStatus === 503
        ? { status: 503, json: { message: 'Obsolete first channel error' } }
        : { json: { data: { items: [scopedMessage(old ? 1010 : 1011, old ? 'Obsolete first channel history' : 'Second channel message')], hasMore: false } } });
      return true;
    } });
    await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('status', { name: 'Đang tải lịch sử tin nhắn' }).waitFor();
    await page.locator('.group-channel-item').filter({ hasText: 'second-channel' }).click();
    const response = page.waitForResponse(response => response.url().includes('/channels/1010/messages'));
    oldGate.resolve(); await (await response).finished(); await settleUI(page);
    assert.equal(await page.getByRole('status', { name: 'Đang tải lịch sử tin nhắn' }).isVisible(), true);
    assert.equal(await page.getByText('Obsolete first channel history', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Obsolete first channel error', { exact: true }).count(), 0);
    newGate.resolve();
    await page.getByText('Second channel message', { exact: true }).waitFor();
    assert.deepEqual(errors, []);
  });
}

test('late pagination cannot append old messages or cursor state to another server', async t => {
  const gate = deferred(t);
  const { page, errors } = await openScopedChat({ handler: async (route, pathname) => {
    if (pathname !== '/api/channels/1010/messages') return false;
    const before = new URL(route.request().url()).searchParams.get('before');
    if (before) await gate.promise;
    const items = before ? [scopedMessage(9000, 'Obsolete pagination message')]
      : Array.from({ length: 60 }, (_, index) => ({ ...scopedMessage(6000 + index, `History line ${index}`), channelId: 1010 }));
    await route.fulfill({ json: { data: { items, hasMore: true, nextCursor: before ? 800 : 900 } } });
    return true;
  } });
  await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
  await page.getByText('History line 59', { exact: true }).waitFor();
  const request = page.waitForRequest(request => new URL(request.url()).searchParams.has('before'));
  await page.getByRole('region', { name: 'Tin nhắn kênh #alpha-channel' }).evaluate(element => {
    element.scrollTop = 0;
    element.dispatchEvent(new Event('scroll', { bubbles: true }));
  });
  await request;
  await switchScopedServer(page, 'Beta');
  await page.getByText('Beta message', { exact: true }).waitFor();
  const response = page.waitForResponse(response => new URL(response.url()).searchParams.has('before'));
  gate.resolve(); await (await response).finished(); await settleUI(page);
  assert.equal(await page.getByText('Obsolete pagination message', { exact: true }).count(), 0);
  assert.equal(await page.getByText('Beta message', { exact: true }).count(), 1);
  assert.equal(await page.getByRole('status', { name: 'Đang tải thêm tin nhắn cũ' }).count(), 0);
  assert.deepEqual(errors, []);
});

test('late send result cannot enter another server or clear its draft, and the current channel can send', async t => {
  const gate = deferred(t);
  const sends = [];
  const { page, errors } = await openScopedChat({ handler: async (route, pathname) => {
    if (route.request().method() !== 'POST' || !pathname.includes('/messages')) return false;
    sends.push(pathname);
    if (pathname.includes('/1010/')) await gate.promise;
    await route.fulfill({ status: 201, json: { data: { ...scopedMessage(pathname.includes('/1010/') ? 1010 : 1020, route.request().postDataJSON().content), id: 90000 + sends.length, senderId: 100 } } });
    return true;
  } });
  await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
  const input = page.getByRole('textbox', { name: 'Nội dung tin nhắn' });
  await page.getByText('Alpha message', { exact: true }).waitFor();
  const request = page.waitForRequest(request => request.method() === 'POST');
  await input.fill('Pending Alpha send'); await input.press('Enter'); await request;
  await switchScopedServer(page, 'Beta');
  await page.getByText('Beta message', { exact: true }).waitFor();
  await input.fill('New Beta draft');
  const response = page.waitForResponse(response => response.request().method() === 'POST');
  gate.resolve(); await (await response).finished(); await settleUI(page);
  assert.equal(await input.inputValue(), 'New Beta draft');
  assert.equal(await page.getByText('Pending Alpha send', { exact: true }).count(), 0);
  await input.press('Enter');
  await page.getByText('New Beta draft', { exact: true }).waitFor();
  assert.deepEqual(sends, ['/api/channels/1010/messages', '/api/channels/1020/messages']);
  assert.equal(await input.inputValue(), '');
  assert.deepEqual(errors, []);
});

for (const action of ['leave', 'remove']) {
  test(`current ${action} failure keeps its modal usable and retry succeeds`, async () => {
    let attempts = 0, left = false;
    const { page, errors } = await openScopedChat({ owner: action === 'remove', handler: async (route, pathname) => {
      if (pathname === '/api/v1/servers' && left) {
        await route.fulfill({ json: { data: [{ ...scopedServers[1], ownerId: 200 }] } }); return true;
      }
      if (!pathname.endsWith(action === 'leave' ? '/leave' : '/members/300')) return false;
      attempts++;
      if (attempts === 2 && action === 'leave') left = true;
      await route.fulfill(attempts === 1 ? { status: 503, json: { message: 'Temporary failure' } } : { status: 204, body: '' });
      return true;
    } });
    await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
    await page.getByText('Common Member', { exact: true }).waitFor();
    await page.getByRole('button', { name: action === 'leave' ? 'Rời khỏi Server' : 'Loại Common Member khỏi Server', exact: true }).first().click();
    const dialog = page.getByRole('dialog');
    const confirm = dialog.getByRole('button', { name: action === 'leave' ? /Rời Server/ : /Xác nhận loại/ });
    await confirm.click();
    await dialog.getByRole('alert').waitFor();
    assert.equal(await confirm.isEnabled(), true);
    await confirm.click();
    await dialog.waitFor({ state: 'hidden' });
    if (action === 'leave') {
      await page.waitForURL(`${origin}/`);
      await page.getByRole('heading', { name: 'Scope Beta', exact: true }).waitFor();
      assert.equal(await page.getByRole('link', { name: 'Mở nhóm Scope Alpha', exact: true }).count(), 0);
    } else {
      assert.equal(new URL(page.url()).pathname, '/servers/101');
      assert.equal(await page.getByText('Common Member', { exact: true }).count(), 0);
    }
    assert.equal(attempts, 2);
    assert.deepEqual(errors, []);
  });
}

for (const status of [200, 503]) {
  test(`late channel response ${status} from Alpha cannot replace Beta or its messages`, async t => {
    const gate = deferred(t);
    const { page, errors } = await openScopedChat({ handler: async (route, pathname) => {
      if (pathname !== '/api/v1/servers/101/channels') return false;
      await gate.promise;
      await route.fulfill(status === 200 ? { json: { data: scopedChannels(101) } } : { status, json: { message: 'Old channel failure' } });
      return true;
    } });
    await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
    await page.locator('.group-sidebar__identity strong').filter({ hasText: 'Scope Alpha' }).waitFor();
    await switchScopedServer(page, 'Beta');
    await page.getByText('Beta message', { exact: true }).waitFor();
    const response = page.waitForResponse(response => response.url().endsWith('/servers/101/channels'));
    gate.resolve(); await (await response).finished(); await settleUI(page);
    assert.equal(await page.getByText('Alpha message', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Old channel failure', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Beta message', { exact: true }).count(), 1);
    assert.equal(await page.getByRole('button', { name: /alpha-channel/ }).count(), 0);
    const input = page.getByRole('textbox', { name: 'Nội dung tin nhắn' });
    assert.match(await input.getAttribute('placeholder'), /beta-channel/);
    assert.deepEqual(errors, []);
  });

  test(`late history response ${status} from Alpha cannot replace Beta`, async t => {
    const gate = deferred(t);
    const { page, errors } = await openScopedChat({ handler: async (route, pathname) => {
      if (pathname !== '/api/channels/1010/messages') return false;
      await gate.promise;
      await route.fulfill(status === 200 ? { json: { data: { items: [scopedMessage(1010, 'Obsolete Alpha history')], hasMore: true, nextCursor: 9 } } } : { status, json: { message: 'Obsolete history failure' } });
      return true;
    } });
    await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('status', { name: 'Đang tải lịch sử tin nhắn' }).waitFor();
    await switchScopedServer(page, 'Beta');
    await page.getByText('Beta message', { exact: true }).waitFor();
    const response = page.waitForResponse(response => response.url().includes('/channels/1010/messages'));
    gate.resolve(); await (await response).finished(); await settleUI(page);
    assert.equal(await page.getByText('Obsolete Alpha history', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Obsolete history failure', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Beta message', { exact: true }).count(), 1);
    assert.deepEqual(errors, []);
  });
}

test('Alpha to Beta to Alpha ignores the channel response from the first Alpha visit', async t => {
  const gate = deferred(t);
  let firstVisit = true;
  const { page, errors } = await openScopedChat({ handler: async (route, pathname) => {
    if (pathname !== '/api/v1/servers/101/channels' || !firstVisit) return false;
    await gate.promise;
    await route.fulfill({ json: { data: [{ id: 1019, serverId: 101, name: 'obsolete-channel' }] } });
    return true;
  } });
  await page.goto(`${origin}/servers/101`, { waitUntil: 'domcontentloaded' });
  await page.locator('.group-sidebar__identity strong').filter({ hasText: 'Scope Alpha' }).waitFor();
  await switchScopedServer(page, 'Beta');
  firstVisit = false;
  await switchScopedServer(page, 'Alpha');
  await page.getByText('Alpha message', { exact: true }).waitFor();
  const response = page.waitForResponse(response => response.url().endsWith('/servers/101/channels'));
  gate.resolve(); await (await response).finished(); await settleUI(page);
  assert.equal(await page.getByRole('button', { name: /obsolete-channel/ }).count(), 0);
  assert.equal(await page.getByText('Alpha message', { exact: true }).count(), 1);
  assert.deepEqual(errors, []);
});

for (const action of ['leave', 'remove']) {
  for (const status of [204, 503]) {
    test(`late ${action} response ${status} cannot redirect or mutate Beta`, async t => {
      const gate = deferred(t);
      const method = action === 'leave' ? 'POST' : 'DELETE';
      const suffix = action === 'leave' ? '/leave' : '/members/300';
      let left = false;
      const { page, errors } = await openScopedChat({ owner: action === 'remove', handler: async (route, pathname) => {
        if (pathname === '/api/v1/servers' && left) {
          await route.fulfill({ json: { data: [{ ...scopedServers[1], ownerId: 200 }] } });
          return true;
        }
        if (route.request().method() !== method || !pathname.endsWith(suffix)) return false;
        await gate.promise;
        if (action === 'leave' && status === 204) left = true;
        await route.fulfill(status === 204 ? { status, body: '' } : { status, json: { message: 'Obsolete action failure' } });
        return true;
      } });
      await page.goto(`${origin}/servers/102`, { waitUntil: 'domcontentloaded' });
      await page.getByText('Common Member', { exact: true }).waitFor();
      await switchScopedServer(page, 'Alpha');
      if (action === 'leave') await page.getByRole('button', { name: 'Rời khỏi Server', exact: true }).first().click();
      else await page.getByRole('button', { name: 'Loại Common Member khỏi Server', exact: true }).click();
      const request = page.waitForRequest(request => request.method() === method);
      await page.getByRole('dialog').getByRole('button', { name: action === 'leave' ? /Rời Server/ : /Xác nhận loại/ }).click();
      await request;
      await page.goBack({ waitUntil: 'domcontentloaded' });
      await page.getByText('Beta message', { exact: true }).waitFor();
      await page.getByText('Common Member', { exact: true }).waitFor();
      const response = page.waitForResponse(response => response.request().method() === method);
      gate.resolve(); await (await response).finished(); await settleUI(page);
      assert.equal(new URL(page.url()).pathname, '/servers/102');
      assert.equal(await page.getByText('Common Member', { exact: true }).count(), 1);
      assert.equal(await page.getByRole('dialog').count(), 0);
      if (action === 'leave' && status === 204) {
        assert.equal(await page.getByRole('link', { name: 'Mở nhóm Scope Alpha', exact: true }).count(), 0);
      }
      assert.deepEqual(errors, []);
    });
  }
}

async function openComposer() {
  const result = await openPage();
  await result.page.goto(`${origin}/__group-ui-test__`, { waitUntil: 'domcontentloaded' });
  await result.page.getByRole('textbox', { name: 'Nội dung tin nhắn' }).waitFor();
  return result;
}

async function screenshot(page, name) {
  if (!process.env.GROUP_UI_ARTIFACT_DIR) return;
  await mkdir(process.env.GROUP_UI_ARTIFACT_DIR, { recursive: true });
  await page.screenshot({ path: path.join(process.env.GROUP_UI_ARTIFACT_DIR, name), fullPage: true });
}

const iconFile = { name: 'server.png', mimeType: 'image/png', buffer: Buffer.from('fixture image') };
const uploadedIcon = 'https://images.unsplash.com/new-upload.png';

async function submitServer(page, name = 'Server vừa tạo') {
  const dialog = page.getByRole('dialog');
  await dialog.getByPlaceholder('Ví dụ: Nhóm Học SBA301').fill(name);
  await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).click();
}

for (const width of [1440, 375]) {
  test(`successful creation opens the returned Server ID and general at viewport ${width}`, async () => {
    const created = { id: 87, ownerId: 100, name: 'New Server', iconUrl: null };
    const { page, requests, errors } = await openPage({ width, handler: async route => {
      if (route.request().method() === 'POST') {
        assert.equal(new URL(route.request().url()).pathname, '/api/v1/servers');
        assert.equal(route.request().postDataJSON().name, created.name);
        return route.fulfill({ status: 201, json: { data: created } });
      }
      return fulfillServerRequest(route, [created]);
    } });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await openCreateModal(page);
    await submitServer(page, created.name);
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await page.waitForURL(`${origin}/servers/87`);
    const input = page.getByRole('textbox', { name: 'Nội dung tin nhắn' });
    await input.waitFor();
    assert.equal(await input.isEnabled(), true);
    assert.match(await input.getAttribute('placeholder'), /general/);
    assert.ok(requests.some(request => request.path === '/api/v1/servers/87'));
    assert.ok(requests.some(request => request.path === '/api/v1/servers/87/channels'));
    assert.equal(await page.getByRole('link', { name: 'Mở nhóm New Server', exact: true }).count(), 1);
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await page.getByRole('heading', { name: created.name, exact: true }).waitFor();
    assert.equal(new URL(page.url()).pathname, '/');
    assert.deepEqual(errors, []);
  });
}

for (const status of [400, 503]) {
  test(`failed creation ${status} stays on the current page and retry opens the new Server`, async () => {
    const created = { id: 87, ownerId: 100, name: 'Retry Server', iconUrl: null };
    let attempts = 0;
    const { page, errors } = await openPage({ handler: async route => {
      if (route.request().method() === 'POST') {
        attempts++;
        return route.fulfill(attempts === 1 ? { status, json: { message: `Create rejected ${status}` } } : { status: 201, json: { data: created } });
      }
      return fulfillServerRequest(route, [created]);
    } });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await openCreateModal(page);
    await submitServer(page, created.name);
    await page.getByText(`Create rejected ${status}`, { exact: true }).waitFor();
    assert.equal(new URL(page.url()).pathname, '/');
    assert.equal(await page.getByRole('dialog').isVisible(), true);
    assert.equal(await page.getByPlaceholder('Ví dụ: Nhóm Học SBA301').inputValue(), created.name);
    await page.getByRole('dialog').locator('form').evaluate(form => form.requestSubmit());
    await page.waitForURL(`${origin}/servers/87`);
    await page.locator('.group-sidebar__identity strong').filter({ hasText: created.name }).waitFor();
    assert.equal(attempts, 2);
    assert.deepEqual(errors, []);
  });
}

for (const { closeForm, revisit } of [{ closeForm: true }, { closeForm: false }, { closeForm: false, revisit: true }]) {
  test(`late creation cannot take over ${revisit ? 'a returned history entry' : 'a newer route'} when form was ${closeForm ? 'closed' : 'still open'}`, async t => {
    const gate = deferred(t);
    const created = { id: 87, ownerId: 100, name: 'Late Server', iconUrl: null };
    const { page, errors } = await openPage({ handler: async route => {
      if (route.request().method() === 'POST') {
        await gate.promise;
        return route.fulfill({ status: 201, json: { data: created } });
      }
      return fulfillServerRequest(route, [created]);
    } });
    await page.goto(`${origin}/servers/12`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: 'Trang chủ DenHub', exact: true }).click();
    await openCreateModal(page);
    const request = page.waitForRequest(request => request.method() === 'POST');
    await submitServer(page, created.name); await request;
    if (closeForm) {
      await page.getByRole('dialog').getByRole('button', { name: 'Hủy bỏ', exact: true }).click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });
    }
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await page.waitForURL(`${origin}/servers/12`);
    if (revisit) {
      await page.goForward({ waitUntil: 'domcontentloaded' });
      await page.waitForURL(`${origin}/`);
    }
    const response = page.waitForResponse(response => response.request().method() === 'POST');
    gate.resolve(); await (await response).finished(); await settleUI(page);
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    assert.equal(new URL(page.url()).pathname, revisit ? '/' : '/servers/12');
    assert.equal(await page.getByRole('link', { name: 'Mở nhóm Late Server', exact: true }).count(), 1);
    assert.deepEqual(errors, []);
  });
}

for (const oldStatus of [200, 503]) {
  test(`server creation keeps the new server visible when an old list returns ${oldStatus}`, async t => {
    let releaseOld, releaseFresh;
    const oldGate = new Promise(resolve => { releaseOld = resolve; });
    const freshGate = new Promise(resolve => { releaseFresh = resolve; });
    t.after(() => { releaseOld(); releaseFresh(); });
    let didCreate = false;
    let freshLists = 0;
    const created = { id: 33, name: 'Server vừa tạo', ownerId: 100, iconUrl: null };
    const { page, errors } = await openPage({ handler: async route => {
      if (new URL(route.request().url()).pathname !== '/api/v1/servers') return fulfillServerRequest(route, [created]);
      if (route.request().method() === 'POST') {
        didCreate = true;
        return route.fulfill({ status: 201, json: { data: created } });
      }
      const fresh = didCreate;
      if (fresh) freshLists++;
      await (fresh ? freshGate : oldGate);
      return route.fulfill(!fresh && oldStatus === 503
        ? { status: 503, json: { detail: 'Old failure' } }
        : { json: { data: fresh ? [created, ...servers] : servers } });
    } });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('status', { name: 'Đang tải danh sách Server' }).waitFor();
    await openCreateModal(page);
    await submitServer(page);
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await page.waitForURL(`${origin}/servers/33`);
    await page.locator('.group-sidebar__identity strong').filter({ hasText: created.name }).waitFor();
    const oldResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/servers' && response.request().method() === 'GET');
    releaseOld();
    await (await oldResponse).finished();
    await settleUI(page);
    assert.equal(new URL(page.url()).pathname, '/servers/33');
    assert.equal(await page.getByRole('link', { name: `Mở nhóm ${created.name}`, exact: true }).count(), 1);
    releaseFresh();
    await page.getByRole('link', { name: 'Mở nhóm Nhóm học tập', exact: true }).waitFor();
    await page.getByRole('link', { name: 'Trang chủ DenHub', exact: true }).click();
    await page.getByRole('heading', { name: 'Không Gian Của Bạn (3)' }).waitFor();
    assert.ok(freshLists >= 1);
    assert.equal(await page.getByRole('heading', { name: 'Nhóm học tập', exact: true }).count(), 1);
    assert.equal(await page.getByRole('heading', { name: 'Chưa tải được danh sách Server' }).count(), 0);
    assert.deepEqual(errors, []);
  });
}

test('creation recovers a failed list and keeps the created server visible if refresh fails', async () => {
  let allowList = false;
  let refreshed = false;
  let didCreate = false;
  const created = { id: 33, name: 'Server vừa tạo', ownerId: 100 };
  const { page, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname !== '/api/v1/servers') return fulfillServerRequest(route, [created]);
    if (route.request().method() === 'POST') {
      didCreate = true;
      return route.fulfill({ status: 201, json: { data: created } });
    }
    if (didCreate) refreshed = true;
    return route.fulfill(!allowList ? { status: 503, json: { detail: 'Unavailable' } } : { json: { data: [created, ...servers] } });
  } });
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chưa tải được danh sách Server' }).waitFor();
  await openCreateModal(page);
  await submitServer(page);
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await page.waitForURL(`${origin}/servers/33`);
  await page.locator('.group-sidebar__identity strong').filter({ hasText: created.name }).waitFor();
  await page.getByRole('link', { name: 'Trang chủ DenHub', exact: true }).click();
  await page.getByText('Chưa tải được đầy đủ danh sách Server', { exact: true }).waitFor();
  assert.equal(refreshed, true);
  allowList = true;
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await page.getByRole('heading', { name: 'Không Gian Của Bạn (3)' }).waitFor();
  assert.deepEqual(errors, []);
});

test('creation refreshes the list even when the initial GET fails while POST is pending', async t => {
  let releaseList, releaseCreate;
  const listGate = new Promise(resolve => { releaseList = resolve; });
  const createGate = new Promise(resolve => { releaseCreate = resolve; });
  t.after(() => { releaseList(); releaseCreate(); });
  let committed = false;
  const created = { id: 33, name: 'Server vừa tạo', ownerId: 100 };
  const { page, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname !== '/api/v1/servers') return fulfillServerRequest(route, [created]);
    if (route.request().method() === 'POST') {
      await createGate;
      committed = true;
      return route.fulfill({ status: 201, json: { data: created } });
    }
    if (committed) return route.fulfill({ json: { data: [created, ...servers] } });
    await listGate;
    return route.fulfill({ status: 503, json: { detail: 'Initial list failed' } });
  } });
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  await openCreateModal(page);
  const post = page.waitForRequest(request => request.method() === 'POST');
  await submitServer(page);
  await post;
  releaseList();
  await page.getByText('Chưa tải được danh sách Server', { exact: true }).waitFor();
  releaseCreate();
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await page.waitForURL(`${origin}/servers/33`);
  await page.getByRole('link', { name: 'Trang chủ DenHub', exact: true }).click();
  await page.getByRole('heading', { name: 'Không Gian Của Bạn (3)' }).waitFor();
  assert.equal(await page.getByRole('heading', { name: created.name, exact: true }).count(), 1);
  assert.deepEqual(errors, []);
});

for (const oldStatus of [201, 503]) {
  test(`an old create response ${oldStatus} cannot close or unlock a new pending form`, async t => {
    let releaseOld, releaseNew;
    const oldGate = new Promise(resolve => { releaseOld = resolve; });
    const newGate = new Promise(resolve => { releaseNew = resolve; });
    t.after(() => { releaseOld(); releaseNew(); });
    let creates = 0;
    const createdServers = [];
    const { page, errors } = await openPage({ handler: async route => {
      if (route.request().method() !== 'POST') return fulfillServerRequest(route, createdServers);
      const current = ++creates;
      await (current === 1 ? oldGate : newGate);
      const created = { id: current === 1 ? 33 : 34, ownerId: 100, ...route.request().postDataJSON() };
      if (current !== 1 || oldStatus === 201) createdServers.push(created);
      return route.fulfill(current === 1 && oldStatus === 503
        ? { status: 503, json: { detail: 'Old create error' } }
        : { status: 201, json: { data: created } });
    } });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await openCreateModal(page);
    const oldRequest = page.waitForRequest(request => request.method() === 'POST');
    await submitServer(page, 'Server cũ');
    await oldRequest;
    await page.getByRole('dialog').getByRole('button', { name: 'Hủy bỏ', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await openCreateModal(page);
    const newRequest = page.waitForRequest(request => request.method() === 'POST');
    await submitServer(page, 'Bản nháp mới');
    await newRequest;
    const oldResponse = page.waitForResponse(response => response.request().method() === 'POST' && response.request().postDataJSON().name === 'Server cũ');
    releaseOld();
    await (await oldResponse).finished();
    await settleUI(page);
    const dialog = page.getByRole('dialog');
    assert.equal(await dialog.isVisible(), true);
    assert.equal(new URL(page.url()).pathname, '/');
    assert.equal(await dialog.getByPlaceholder('Ví dụ: Nhóm Học SBA301').inputValue(), 'Bản nháp mới');
    await dialog.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
    await settleUI(page);
    assert.equal(creates, 2);
    assert.equal(await page.getByText('Old create error', { exact: true }).count(), 0);
    if (oldStatus === 201) await page.getByRole('link', { name: 'Mở nhóm Server cũ', exact: true }).waitFor();
    releaseNew();
    await dialog.waitFor({ state: 'hidden' });
    await page.waitForURL(`${origin}/servers/34`);
    await page.locator('.group-sidebar__identity strong').filter({ hasText: 'Bản nháp mới' }).waitFor();
    assert.deepEqual(errors, []);
  });
}

test('create form validates whitespace and the 100 UTF-16 boundary and blocks duplicate submit', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  t.after(() => release());
  const creates = [];
  let created;
  const { page, errors } = await openPage({ handler: async route => {
    if (route.request().method() !== 'POST') return fulfillServerRequest(route, created ? [created] : []);
    creates.push(route.request().postDataJSON());
    await gate;
    created = { id: 33, ownerId: 100, ...creates[0] };
    return route.fulfill({ status: 201, json: { data: created } });
  } });
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  await openCreateModal(page);
  const dialog = page.getByRole('dialog');
  const input = dialog.getByPlaceholder('Ví dụ: Nhóm Học SBA301');
  await submitServer(page, '\u00a0\u2003 ');
  await dialog.getByText('Vui lòng đặt tên cho Server', { exact: true }).waitFor();
  assert.equal(creates.length, 0);
  await input.evaluate(element => {
    // Exercise the Form validator even if a browser bypasses the native maxlength.
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, 'a'.repeat(101));
    element.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await dialog.locator('form').evaluate(form => form.requestSubmit());
  await dialog.getByText('Tên Server không được vượt quá 100 ký tự', { exact: true }).waitFor();
  assert.equal(creates.length, 0);
  const name = '🙂'.repeat(50);
  assert.equal(name.length, 100);
  await input.fill(name);
  const request = page.waitForRequest(request => request.method() === 'POST');
  await dialog.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
  await request;
  await settleUI(page);
  assert.equal(creates.length, 1);
  assert.equal(creates[0].name, name);
  release();
  await dialog.waitFor({ state: 'hidden' });
  await page.waitForURL(`${origin}/servers/33`);
  await page.locator('.group-sidebar__identity strong').filter({ hasText: name }).waitFor();
  assert.deepEqual(errors, []);
});

test('home server link opens the exact server with the keyboard', async () => {
  const { page, errors } = await openPage();
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  const link = page.getByRole('link', { name: 'Mở Server DenHub Design', exact: true });
  await link.focus();
  await page.keyboard.press('Enter');
  await page.waitForURL(`${origin}/servers/12`);
  await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design', exact: true }).waitFor();
  assert.deepEqual(errors, []);
});

for (const width of [320, 375, 768, 1440]) {
  test(`home server list fits viewport ${width} and keeps navigation accessible`, async () => {
    const { page, errors } = await openPage({ width });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('heading', { name: 'Không Gian Của Bạn (2)' }).waitFor();
    const title = page.getByRole('heading', { name: 'Nhóm học tập', exact: true });
    assert.equal(await title.isVisible(), true);
    const bounds = await title.boundingBox();
    assert.ok(bounds.width > 30, JSON.stringify(bounds));
    assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1, JSON.stringify(bounds));
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    if (width < 992) {
      const nav = page.getByRole('navigation', { name: 'Điều hướng trang chủ' });
      await nav.getByRole('link', { name: 'Tin nhắn', exact: true }).waitFor();
      assert.equal(await nav.getByRole('link', { name: 'Bạn bè', exact: true }).getAttribute('href'), '/friends');
      assert.equal(await nav.getByRole('link', { name: 'Hồ sơ', exact: true }).getAttribute('href'), '/profile');
    } else {
      await page.getByRole('link', { name: 'Trang Tổng Quan', exact: true }).waitFor();
    }
    await screenshot(page, `home-server-list-${width}.png`);
    assert.deepEqual(errors, []);
  });
}

async function openCreateModal(page) {
  await page.getByText('Tạo Không Gian (Server) Mới', { exact: true }).click();
  await page.getByRole('dialog').waitFor();
}

async function settleUI(page) {
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

test('home distinguishes loading, failed list with retry, and a successful empty list', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let mode = 'failure';
  const { page, errors } = await openPage({ handler: async route => {
    await gate;
    if (mode === 'failure') return route.fulfill({ status: 503, json: { message: 'Unavailable' } });
    return route.fulfill({ json: { data: mode === 'empty' ? [] : servers } });
  } });
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('status', { name: 'Đang tải danh sách Server' }).waitFor();
  assert.equal(await page.getByText('Bạn chưa có Server nào', { exact: true }).count(), 0);
  release();
  await page.getByRole('heading', { name: 'Chưa tải được danh sách Server' }).waitFor();
  assert.equal(await page.getByText('Bạn chưa có Server nào', { exact: true }).count(), 0);
  mode = 'success';
  await page.getByRole('alert').filter({ hasText: 'Chưa tải được danh sách Server' })
    .getByRole('button', { name: 'Thử lại', exact: true }).click();
  await page.getByRole('heading', { name: 'Không Gian Của Bạn (2)' }).waitFor();
  await page.getByRole('heading', { name: 'Nhóm học tập', exact: true }).waitFor();
  mode = 'empty';
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Bạn chưa có Server nào' }).waitFor();
  assert.equal(await page.getByRole('heading', { name: 'Chưa tải được danh sách Server' }).count(), 0);
  assert.deepEqual(errors, []);
});

test('creation waits for icon upload, blocks Enter/form submission, and sends the uploaded URL', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const creates = [];
  let created;
  const { page, requests, errors } = await openPage({ handler: async route => {
    const request = route.request();
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/files/upload') {
      await gate;
      return route.fulfill({ json: { data: { url: uploadedIcon } } });
    }
    if (request.method() === 'POST') {
      assert.equal(new URL(request.url()).pathname, '/api/v1/servers');
      creates.push(request.postDataJSON());
      created = { id: 33, ownerId: 100, ...creates.at(-1) };
      return route.fulfill({ status: 201, json: { data: created } });
    }
    return fulfillServerRequest(route, created ? [created] : []);
  } });
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  await openCreateModal(page);
  const dialog = page.getByRole('dialog');
  const upload = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/files/upload');
  await dialog.locator('input[type=file]').setInputFiles(iconFile);
  await upload;
  await dialog.getByText('ĐANG TẢI...', { exact: true }).waitFor();
  assert.equal(await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).isDisabled(), true);
  await dialog.getByPlaceholder('Ví dụ: Nhóm Học SBA301').press('Enter');
  await dialog.locator('form').evaluate(form => form.requestSubmit());
  await settleUI(page);
  assert.equal(creates.length, 0);
  const response = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/files/upload');
  release();
  await (await response).finished();
  await page.waitForFunction(url => document.querySelector('img[alt=avatar]')?.getAttribute('src') === url, uploadedIcon);
  await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.deepEqual(creates, [{ name: 'Không Gian Của Tôi', iconUrl: uploadedIcon }]);
  assert.equal(requests.filter(request => request.method === 'POST' && request.path === '/api/v1/servers').length, 1);
  await page.waitForURL(`${origin}/servers/33`);
  await page.locator('.group-sidebar__identity strong').filter({ hasText: 'Không Gian Của Tôi' }).waitFor();
  assert.deepEqual(errors, []);
});

test('closed modal ignores old upload success/failure and cannot clear a new pending upload', async () => {
  for (const oldStatus of [200, 503]) {
    let releaseOld, releaseNew;
    const oldGate = new Promise(resolve => { releaseOld = resolve; });
    const newGate = new Promise(resolve => { releaseNew = resolve; });
    let uploads = 0;
    const { page, errors } = await openPage({ handler: async route => {
      if (route.request().method() !== 'POST') return fulfillServerRequest(route);
      assert.equal(new URL(route.request().url()).pathname, '/api/v1/files/upload');
      const isOld = ++uploads === 1;
      await (isOld ? oldGate : newGate);
      return route.fulfill({ status: isOld ? oldStatus : 200, json: isOld
        ? { data: { url: 'https://images.unsplash.com/old-upload.png' }, message: 'Stale upload error' }
        : { data: { url: uploadedIcon } } });
    } });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await openCreateModal(page);
    const dialog = page.getByRole('dialog');
    const oldRequest = page.waitForRequest(request => request.method() === 'POST');
    await dialog.locator('input[type=file]').setInputFiles(iconFile);
    await oldRequest;
    await dialog.getByRole('button', { name: 'Hủy bỏ', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    await openCreateModal(page);
    const newRequest = page.waitForRequest(request => request.method() === 'POST');
    await dialog.locator('input[type=file]').setInputFiles(iconFile);
    await newRequest;
    const oldResponse = page.waitForResponse(response => response.request().method() === 'POST');
    releaseOld();
    await (await oldResponse).finished();
    await settleUI(page);
    assert.equal(await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).isDisabled(), true);
    assert.equal(await dialog.getByText('ĐANG TẢI...', { exact: true }).isVisible(), true);
    assert.equal(await page.getByText('Stale upload error', { exact: true }).count(), 0);
    assert.equal(await page.getByText('Đã tải ảnh lên máy chủ thành công!', { exact: true }).count(), 0);
    const newResponse = page.waitForResponse(response => response.request().method() === 'POST');
    releaseNew();
    await (await newResponse).finished();
    await page.waitForFunction(url => document.querySelector('img[alt=avatar]')?.getAttribute('src') === url, uploadedIcon);
    assert.equal(await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).isEnabled(), true);
    assert.deepEqual(errors, []);
  }
});

test('preset or template selection supersedes pending uploads without a late icon or toast', async () => {
  for (const selection of ['preset', 'template']) {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const { page, errors } = await openPage({ handler: async route => {
      if (route.request().method() !== 'POST') return fulfillServerRequest(route);
      await gate;
      return route.fulfill({ json: { url: uploadedIcon } });
    } });
    await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
    await openCreateModal(page);
    const dialog = page.getByRole('dialog');
    const upload = page.waitForRequest(request => request.method() === 'POST');
    await dialog.locator('input[type=file]').setInputFiles(iconFile);
    await upload;
    if (selection === 'preset') await dialog.locator('img[alt=preset]').nth(1).click();
    else await dialog.getByText('Học tập', { exact: true }).click();
    const selectedIcon = await dialog.locator('img[alt=avatar]').getAttribute('src');
    assert.equal(await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).isEnabled(), true);
    const response = page.waitForResponse(response => response.request().method() === 'POST');
    release();
    await (await response).finished();
    await settleUI(page);
    assert.equal(await dialog.locator('img[alt=avatar]').getAttribute('src'), selectedIcon);
    assert.equal(await page.getByText('Đã tải ảnh lên máy chủ thành công!', { exact: true }).count(), 0);
    assert.deepEqual(errors, []);
  }
});

test('a newer file supersedes the old upload and current upload errors leave creation usable', async () => {
  let releaseOld, releaseNew;
  const oldGate = new Promise(resolve => { releaseOld = resolve; });
  const newGate = new Promise(resolve => { releaseNew = resolve; });
  let uploads = 0;
  const { page, errors } = await openPage({ handler: async route => {
    if (route.request().method() !== 'POST') return fulfillServerRequest(route);
    const isOld = ++uploads === 1;
    await (isOld ? oldGate : newGate);
    return route.fulfill({ status: isOld ? 200 : 503, json: isOld ? { url: uploadedIcon } : { message: 'Current upload error' } });
  } });
  await page.goto(`${origin}/`, { waitUntil: 'domcontentloaded' });
  await openCreateModal(page);
  const dialog = page.getByRole('dialog');
  const originalIcon = await dialog.locator('img[alt=avatar]').getAttribute('src');
  for (let i = 0; i < 2; i++) {
    const request = page.waitForRequest(request => request.method() === 'POST');
    await dialog.locator('input[type=file]').setInputFiles(iconFile);
    await request;
  }
  let response = page.waitForResponse(response => response.request().method() === 'POST');
  releaseOld();
  await (await response).finished();
  await settleUI(page);
  assert.equal(await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).isDisabled(), true);
  response = page.waitForResponse(response => response.request().method() === 'POST');
  releaseNew();
  await (await response).finished();
  await page.getByText('Current upload error', { exact: true }).waitFor();
  assert.equal(await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).isEnabled(), true);
  assert.equal(await dialog.locator('img[alt=avatar]').getAttribute('src'), originalIcon);
  assert.deepEqual(errors, []);
});

test('group route uses server detail instead of the list snapshot, reloads, and switches exact IDs', async () => {
  const { page, requests, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname === '/api/v1/servers/1') {
      assert.match(route.request().headers().authorization, /^Bearer fixture\./);
      return route.fulfill({ json: { data: { ...servers[0], name: 'Nhóm học tập mới' } } });
    }
    return fulfillServerRequest(route);
  } });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chào mừng đến với Nhóm học tập mới' }).waitFor();
  assert.equal(await page.getByRole('textbox').isDisabled(), true);
  assert.equal(await page.getByRole('button', { name: 'Gửi tin nhắn' }).isDisabled(), true);
  await page.getByRole('link', { name: 'Mở nhóm DenHub Design' }).click();
  await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).waitFor();
  assert.equal(await page.locator('a[aria-current="page"]').count(), 1);
  assert.equal(await page.locator('a[aria-current="page"]').getAttribute('href'), '/servers/12');
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).waitFor();
  await screenshot(page, 'group-desktop.png');
  await page.getByRole('button', { name: 'Ẩn danh sách thành viên' }).click();
  assert.equal(await page.getByRole('complementary', { name: 'Thành viên của nhóm' }).count(), 0);
  await page.getByRole('button', { name: 'Hiện danh sách thành viên' }).click();
  assert.equal(await page.getByRole('complementary', { name: 'Thành viên của nhóm' }).count(), 1);
  assert.ok(requests.some(request => request.path === '/api/v1/servers/1'));
  assert.ok(requests.filter(request => request.path === '/api/v1/servers/12').length >= 2);
  assert.ok(requests.every(request => request.method === 'GET'));
  assert.deepEqual(errors, []);
});

test('detail loading, service failure and retry are distinct from a missing group', async () => {
  let respond;
  const gate = new Promise(resolve => { respond = resolve; });
  const { page, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname === '/api/v1/servers') return fulfillServerRequest(route);
    await gate;
    await route.fulfill({ status: 503, json: { message: 'Unavailable' } });
  } });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('status', { name: 'Đang tải nhóm' }).waitFor();
  // Both StrictMode requests share the gate and are released together.
  respond();
  await page.getByRole('heading', { name: 'Chưa tải được nhóm' }).waitFor();
  await page.context().unroute(apiPattern);
  await page.context().route(apiPattern, route => fulfillServerRequest(route));
  await page.getByRole('button', { name: 'Thử lại' }).click();
  await page.getByRole('heading', { name: 'Chào mừng đến với Nhóm học tập' }).waitFor();
  await page.goto(`${origin}/servers/999`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Không tìm thấy nhóm của bạn' }).waitFor();
  assert.deepEqual(errors, []);
});

test('server detail remains accessible when the dock list fails or does not contain the server', async () => {
  for (const failedList of [true, false]) {
    const { page, errors } = await openPage({ handler: async route => {
      if (new URL(route.request().url()).pathname === '/api/v1/servers') {
        return failedList ? route.fulfill({ status: 503, json: { message: 'Unavailable' } })
          : route.fulfill({ json: { data: [] } });
      }
      return fulfillServerRequest(route);
    } });
    await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('heading', { name: 'Chào mừng đến với Nhóm học tập' }).waitFor();
    assert.deepEqual(errors, []);
  }
});

test('detail 403 redirects a direct visit to home without a permission error or logout', async () => {
  const { page, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname === '/api/v1/servers') return fulfillServerRequest(route);
    return route.fulfill({ status: 403, json: { status: 403, detail: 'internal details must not be displayed' } });
  } });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.waitForURL(`${origin}/`);
  await page.getByRole('heading', { name: 'Xin chào, UI Test! 👋' }).waitFor();
  assert.equal(await page.locator('.group-workspace').count(), 0);
  assert.equal(await page.getByText('Bạn không có quyền truy cập nhóm', { exact: false }).count(), 0);
  assert.equal(await page.getByText('internal details must not be displayed').count(), 0);
  assert.match(await page.evaluate(() => localStorage.getItem('denhub_access_token')), /^fixture\./);
  assert.deepEqual(errors, []);
});

test('detail 403 replaces the denied route so Back returns to the previous allowed server', async () => {
  const { page, requests, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname === '/api/v1/servers/1') {
      return route.fulfill({ status: 403, json: { status: 403 } });
    }
    return fulfillServerRequest(route);
  } });
  await page.goto(`${origin}/servers/12`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).waitFor();
  await page.getByRole('link', { name: 'Mở nhóm Nhóm học tập' }).click();
  await page.waitForURL(`${origin}/`);
  await page.getByRole('heading', { name: 'Xin chào, UI Test! 👋' }).waitFor();
  assert.equal(await page.getByText('Bạn không có quyền truy cập nhóm', { exact: false }).count(), 0);
  const deniedRequests = requests.filter(request => request.path === '/api/v1/servers/1').length;
  assert.ok(deniedRequests > 0);
  await page.goBack();
  await page.waitForURL(`${origin}/servers/12`);
  await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).waitFor();
  assert.equal(requests.filter(request => request.path === '/api/v1/servers/1').length, deniedRequests);
  assert.match(await page.evaluate(() => localStorage.getItem('denhub_access_token')), /^fixture\./);
  assert.deepEqual(errors, []);
});

test('network failure can retry the detail endpoint', async () => {
  let fail = true;
  const { page, requests, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname !== '/api/v1/servers' && fail) return route.abort('failed');
    return fulfillServerRequest(route);
  } });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chưa tải được nhóm' }).waitFor();
  fail = false;
  await page.getByRole('button', { name: 'Thử lại' }).click();
  await page.getByRole('heading', { name: 'Chào mừng đến với Nhóm học tập' }).waitFor();
  assert.ok(requests.filter(request => request.path === '/api/v1/servers/1').length >= 2);
  assert.deepEqual(errors, []);
});

test('detail 401 uses the existing session expiry redirect', async () => {
  const { page, errors } = await openPage({ handler: async route => {
    if (new URL(route.request().url()).pathname === '/api/v1/servers') return fulfillServerRequest(route);
    return route.fulfill({ status: 401, json: { status: 401, title: 'Unauthorized' } });
  } });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.waitForURL('**/login');
  assert.equal(await page.evaluate(() => localStorage.getItem('denhub_access_token')), null);
  assert.deepEqual(errors, []);
});

test('late detail success or failure from a previous server cannot replace the current server', async () => {
  for (const lateStatus of [200, 403]) {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const completions = [];
    const { page, errors } = await openPage({ handler: async route => {
      if (new URL(route.request().url()).pathname !== '/api/v1/servers/1') return fulfillServerRequest(route);
      let completed;
      completions.push(new Promise(resolve => { completed = resolve; }));
      await gate;
      await route.fulfill({ status: lateStatus, json: lateStatus === 200 ? { data: servers[0] } : { status: 403 } });
      completed();
    } });
    const firstRequest = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/servers/1');
    await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
    await firstRequest;
    await page.getByRole('status', { name: 'Đang tải nhóm' }).waitFor();
    await page.getByRole('link', { name: 'Mở nhóm DenHub Design' }).click();
    await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).waitFor();
    const lateResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/servers/1');
    release();
    const response = await lateResponse;
    await response.finished();
    await Promise.all(completions);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.equal(new URL(page.url()).pathname, '/servers/12');
    assert.equal(await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).isVisible(), true);
    assert.equal(await page.locator('.group-state').count(), 0);
    assert.deepEqual(errors, []);
  }
});

test('group route remains behind authentication', async () => {
  const { page, requests } = await openPage({ authenticated: false });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.waitForURL('**/login');
  assert.equal(requests.length, 0);
});

test('mobile and narrow screens fit horizontally and expose channels and members through drawers', async () => {
  const { page, errors } = await openPage({ width: 375 });
  await page.goto(`${origin}/servers/12`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chào mừng đến với DenHub Design' }).waitFor();
  assert.equal(await page.locator('.ant-drawer-open').count(), 0);
  await screenshot(page, 'group-mobile.png');
  await page.getByRole('button', { name: 'Mở danh sách kênh' }).click();
  await page.getByRole('dialog').waitFor();
  assert.equal(await page.getByRole('dialog').getByText(/Chưa có kênh nào trong server|Danh sách kênh chưa sẵn sàng/).isVisible(), true);
  await page.keyboard.press('Escape');
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: 'Mở danh sách thành viên' }).click();
  await page.getByRole('dialog').getByRole('heading', { name: 'Chưa có danh sách thành viên' }).waitFor();
  await page.keyboard.press('Escape');
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  for (const width of [320, 375, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true, `overflow at ${width}`);
    const box = await page.getByRole('button', { name: 'Gửi tin nhắn' }).boundingBox();
    assert.ok(box.x >= 0 && box.x + box.width <= width, `composer outside viewport at ${width}`);
  }
  assert.deepEqual(errors, []);
});

test('composer preserves content, supports Shift+Enter, and only clears after success', async () => {
  const { page, errors } = await openComposer();
  const input = page.getByRole('textbox');
  assert.equal(await page.getByRole('button', { name: 'Gửi tin nhắn' }).isDisabled(), true);
  await input.fill('  Xin chào');
  await input.press('End');
  await input.press('Shift+Enter');
  await input.pressSequentially('nhóm  ');
  const content = await input.inputValue();
  assert.equal(content, '  Xin chào\nnhóm  ');
  await input.press('Enter');
  await page.waitForFunction(() => window.fixture.received.length === 1);
  assert.equal(await input.inputValue(), '');
  assert.deepEqual(await page.evaluate(() => window.fixture.calls), [{ channelId: 10, content }]);
  assert.match(await page.getByRole('status').textContent(), /Xin chào/);
  assert.deepEqual(errors, []);
});

test('composer rejects whitespace and over-limit content and accepts the emoji boundary', async () => {
  const { page } = await openComposer();
  const input = page.getByRole('textbox');
  for (const text of [' \n\t', '\u0085\u2003', '🙂'.repeat(2000) + 'a']) {
    await input.fill(text);
    assert.equal(await page.getByRole('button', { name: 'Gửi tin nhắn' }).isDisabled(), true);
    await input.press('Enter');
  }
  assert.equal(await page.evaluate(() => window.fixture.calls.length), 0);
  await page.getByRole('alert').waitFor();
  await input.fill('🙂'.repeat(2000));
  await input.press('Enter');
  await page.waitForFunction(() => window.fixture.received.length === 1);
});

test('composer holds the draft on 400/401/403/404/network errors and retries successfully', async () => {
  const { page, errors } = await openComposer();
  const input = page.getByRole('textbox');
  await input.fill('Giữ bản nháp này');
  for (const status of [400, 401, 403, 404, 0]) {
    await page.evaluate(status => { window.fixture.mode = 'failure'; window.fixture.status = status; }, status);
    await input.press('Enter');
    await page.getByRole('alert').waitFor();
    assert.equal(await input.inputValue(), 'Giữ bản nháp này');
  }
  assert.equal(await page.evaluate(() => window.fixture.received.length), 0);
  await page.evaluate(() => { window.fixture.mode = 'success'; });
  await page.getByRole('button', { name: 'Gửi tin nhắn' }).click();
  await page.waitForFunction(() => window.fixture.received.length === 1);
  assert.equal(await input.inputValue(), '');
  assert.deepEqual(errors, []);
});

test('composer blocks duplicate sends and keeps an old pending result separate from a new channel draft', async () => {
  const { page, errors } = await openComposer();
  const input = page.getByRole('textbox');
  await page.evaluate(() => { window.fixture.mode = 'pending'; });
  await input.fill('Kênh cũ');
  await input.press('Enter');
  await page.waitForFunction(() => window.fixture.calls.length === 1);
  assert.equal(await input.getAttribute('readonly'), '');
  assert.equal(await input.inputValue(), 'Kênh cũ');
  assert.equal(await page.getByRole('button', { name: 'Gửi tin nhắn' }).isDisabled(), true);
  await input.press('Enter');
  await input.press('Enter');
  assert.equal(await page.evaluate(() => window.fixture.calls.length), 1);
  await page.evaluate(() => window.fixture.setChannelId(20));
  await page.waitForFunction(() => document.querySelector('textarea').value === '');
  await input.fill('Bản nháp kênh mới');
  await page.evaluate(() => window.fixture.resolve({ id: 1, channelId: 10, content: 'Kênh cũ' }));
  await page.waitForFunction(() => window.fixture.received.length === 1);
  assert.equal(await input.inputValue(), 'Bản nháp kênh mới');
  assert.equal(await page.evaluate(() => window.fixture.received[0].channelId), 10);
  assert.deepEqual(errors, []);
});

test('IME composition does not send and missing channels disable the composer', async () => {
  const { page } = await openComposer();
  const input = page.getByRole('textbox');
  await input.fill('Tiếng Việt');
  await input.dispatchEvent('compositionstart');
  await input.press('Enter');
  await input.dispatchEvent('compositionend');
  assert.equal(await page.evaluate(() => window.fixture.calls.length), 0);
  await input.press('Enter');
  await page.waitForFunction(() => window.fixture.received.length === 1);
  await page.evaluate(() => window.fixture.setChannelId(null));
  await page.waitForFunction(() => document.querySelector('textarea').disabled);
  assert.equal(await page.getByRole('button', { name: 'Gửi tin nhắn' }).isDisabled(), true);
});

test('server members list renders members with owner badge, nickname, and fallback avatar', async () => {
  const { page, errors } = await openPage({ handler: async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/servers/12/members') {
      return route.fulfill({
        json: {
          statusCode: 200,
          error: null,
          message: 'OK',
          data: [
            { id: 1, serverId: 12, userId: 100, username: 'hoangnam', name: 'Hoàng Nam', nickname: 'Nam Boss', avatar: null, role: 'OWNER' },
            { id: 2, serverId: 12, userId: 101, username: 'anhben', name: 'Võ Anh Bến', nickname: 'Bến', avatar: null, role: 'MEMBER' },
          ],
        },
      });
    }
    return fulfillServerRequest(route);
  } });
  await page.goto(`${origin}/servers/12`, { waitUntil: 'domcontentloaded' });
  await page.getByText('Thành viên (2)').waitFor();
  await page.getByText('Nam Boss').waitFor();
  assert.equal(await page.getByTitle('Chủ nhóm').isVisible(), true);
  await page.getByText('Bến').waitFor();
  assert.equal(await page.getByText('@hoangnam').isVisible(), true);
  assert.deepEqual(errors, []);
});

test('server members loading, error and retry work correctly', async () => {
  let mode = 'failure';
  const { page, errors } = await openPage({ handler: async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/servers/12/members') {
      if (mode === 'failure') {
        return route.fulfill({ status: 500, json: { message: 'Lỗi tải thành viên' } });
      }
      return route.fulfill({
        json: {
          statusCode: 200,
          error: null,
          message: 'OK',
          data: [
            { id: 1, serverId: 12, userId: 100, username: 'hoangnam', name: 'Hoàng Nam', nickname: 'Nam Boss', avatar: null, role: 'OWNER' },
          ],
        },
      });
    }
    return fulfillServerRequest(route);
  } });
  await page.goto(`${origin}/servers/12`, { waitUntil: 'domcontentloaded' });
  await page.getByText('Lỗi tải thành viên').waitFor();
  const retryBtn = page.getByRole('button', { name: 'Thử lại' });
  assert.equal(await retryBtn.isVisible(), true);
  mode = 'success';
  await retryBtn.click();
  await page.getByText('Nam Boss').waitFor();
  assert.deepEqual(errors, []);
});

test('server switching reloads members of the selected server', async () => {
  const memberRequests = [];
  const { page, errors } = await openPage({ handler: async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/members')) {
      memberRequests.push(url.pathname);
      return route.fulfill({
        json: {
          statusCode: 200,
          error: null,
          message: 'OK',
          data: [
            { id: 1, serverId: Number(url.pathname.split('/')[4]), userId: 100, username: 'user', name: 'User', nickname: 'Nick', avatar: null, role: 'MEMBER' },
          ],
        },
      });
    }
    return fulfillServerRequest(route);
  } });
  await page.goto(`${origin}/servers/12`, { waitUntil: 'domcontentloaded' });
  await page.getByText('Thành viên (1)').waitFor();
  assert.ok(memberRequests.includes('/api/v1/servers/12/members'));
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.getByText('Thành viên (1)').waitFor();
  assert.ok(memberRequests.includes('/api/v1/servers/1/members'));
  assert.deepEqual(errors, []);
});

