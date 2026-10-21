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

async function fulfillServerRequest(route) {
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
    return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: [] } });
  }
  if (pathname.endsWith('/members')) {
    return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: [] } });
  }
  assert.match(pathname, /^\/api\/v1\/servers\/\d+$/);
  const server = servers.find(item => String(item.id) === pathname.split('/').at(-1));
  if (!server) return route.fulfill({ status: 404, contentType: 'application/problem+json', json: { status: 404, title: 'Not Found' } });
  return route.fulfill({ json: { statusCode: 200, error: null, message: 'OK', data: server } });
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
  const { page, requests, errors } = await openPage({ handler: async route => {
    const request = route.request();
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/files/upload') {
      await gate;
      return route.fulfill({ json: { data: { url: uploadedIcon } } });
    }
    if (request.method() === 'POST') {
      assert.equal(new URL(request.url()).pathname, '/api/v1/servers');
      creates.push(request.postDataJSON());
      return route.fulfill({ status: 201, json: { data: { id: 33, ...creates.at(-1) } } });
    }
    return fulfillServerRequest(route);
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
  await page.context().route(apiPattern, fulfillServerRequest);
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

