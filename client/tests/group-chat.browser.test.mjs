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
  await context.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  // Fixtures never reach a real API and are not included in the app bundle.
  if (authenticated) {
    await context.addInitScript(() => {
      const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, sub: 'ui-test@example.invalid' }));
      localStorage.setItem('denhub_access_token', `fixture.${payload}.fixture`);
      localStorage.setItem('denhub_user', JSON.stringify({ id: 100, displayName: 'UI Test', email: 'ui-test@example.invalid' }));
    });
  }
  const requests = [];
  await context.route(apiPattern, async route => {
    const request = route.request();
    requests.push({ method: request.method(), path: new URL(request.url()).pathname });
    if (handler) return handler(route, requests);
    assert.equal(request.method(), 'GET');
    assert.equal(new URL(request.url()).pathname, '/api/v1/servers');
    await route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: servers } });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  return { page, requests, errors };
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

test('group route shows the real server-list response, disables sending, and switches exact server IDs', async () => {
  const { page, requests, errors } = await openPage();
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Chào mừng đến với Nhóm học tập' }).waitFor();
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
  assert.ok(requests.every(request => request.method === 'GET' && request.path === '/api/v1/servers'));
  assert.deepEqual(errors, []);
});

test('loading, failed server list and retry are distinct from a missing group', async () => {
  let respond;
  const gate = new Promise(resolve => { respond = resolve; });
  const { page, errors } = await openPage({ handler: async route => {
    await gate;
    await route.fulfill({ status: 503, json: { message: 'Unavailable' } });
  } });
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('status', { name: 'Đang tải nhóm' }).waitFor();
  // Both StrictMode requests share the gate and are released together.
  respond();
  await page.getByRole('heading', { name: 'Chưa tải được nhóm' }).waitFor();
  await page.context().unroute(apiPattern);
  await page.context().route(apiPattern, route => route.fulfill({ json: { data: servers } }));
  await page.getByRole('button', { name: 'Thử lại' }).click();
  await page.getByRole('heading', { name: 'Chào mừng đến với Nhóm học tập' }).waitFor();
  await page.goto(`${origin}/servers/999`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: 'Không tìm thấy nhóm của bạn' }).waitFor();
  assert.deepEqual(errors, []);
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
  assert.equal(await page.getByRole('dialog').getByText('Danh sách kênh chưa sẵn sàng.').isVisible(), true);
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
