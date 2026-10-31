import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';

const { chromium } = createRequire(import.meta.url)('playwright');
const artifacts = path.join(os.tmpdir(), 'denhub-server-search');
let vite, browser, origin;
before(async () => {
  vite = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), configFile: false,
    plugins: [react()], server: { host: '127.0.0.1', port: 0 } });
  await vite.listen();
  origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  await mkdir(artifacts, { recursive: true });
});
after(async () => { await browser?.close(); await vite?.close(); });

function gate(t) {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  t.after(() => resolve());
  return { promise, resolve };
}

async function open(t, { width = 1440, handler, personal = true, authenticated = true, listFailure = false } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
  t.after(() => context.close());
  context.setDefaultTimeout(12000);
  if (authenticated) await context.addInitScript(() => {
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, sub: 'fixture@example.invalid' }));
    localStorage.setItem('denhub_access_token', `fixture.${payload}.fixture`);
    localStorage.setItem('denhub_user', JSON.stringify({ id: 100, displayName: 'Search fixture' }));
  });
  await context.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  await context.route('https://images.unsplash.com/**', route => route.abort());
  const owned = { id: 1, ownerId: 100, name: 'Java Study', iconUrl: null, visibility: 'PUBLIC' };
  const privateServer = { id: 2, ownerId: 200, name: 'Nhóm Học Tập', iconUrl: null, visibility: 'PRIVATE' };
  const community = { id: 3, ownerId: 200, name: 'Java Community', iconUrl: null, visibility: 'PUBLIC' };
  let joined = false;
  const requests = [], errors = [], creates = [], updates = [];
  await context.route(/^https?:\/\/[^/]+\/api\//, async route => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push({ path: url.pathname, q: url.searchParams.get('q'), page: url.searchParams.get('page'), method: request.method() });
    if (handler && await handler(route, url, { requests, markJoined: () => { joined = true; } })) return;
    if (url.pathname === '/api/v1/servers/discover') {
      const items = (joined ? [] : [community]).filter(server => server.name.toLowerCase().includes((url.searchParams.get('q') || '').toLowerCase()))
        .map(server => ({ id: server.id, name: server.name, iconUrl: server.iconUrl, joined: false }));
      return route.fulfill({ json: { data: { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 } } });
    }
    if (url.pathname === '/api/v1/servers/3/join') {
      joined = true;
      return route.fulfill({ status: 204, body: '' });
    }
    if (url.pathname === '/api/v1/servers' && request.method() === 'POST') {
      creates.push(request.postDataJSON());
      return route.fulfill({ status: 201, json: { data: { ...request.postDataJSON(), id: 4, ownerId: 100 } } });
    }
    if (url.pathname === '/api/v1/servers/1' && request.method() === 'PUT') {
      updates.push(request.postDataJSON());
      Object.assign(owned, request.postDataJSON());
      return route.fulfill({ json: { data: owned } });
    }
    if (url.pathname === '/api/v1/servers') {
      if (listFailure) return route.fulfill({ status: 503, json: {} });
      return route.fulfill({ json: { data: [...(personal ? [owned, privateServer] : []), ...(joined ? [community] : [])] } });
    }
    const id = Number(url.pathname.split('/')[4]);
    if (/\/api\/v1\/servers\/\d+$/.test(url.pathname)) {
      return route.fulfill({ json: { data: [owned, privateServer, community].find(server => server.id === id) || { id, name: 'Created', ownerId: 100 } } });
    }
    if (url.pathname.endsWith('/channels')) return route.fulfill({ json: { data: [] } });
    return route.fulfill({ json: { data: url.pathname.startsWith('/api/posts') ? { result: [], hasMore: false } : [] } });
  });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  return { page, requests, errors, creates, updates };
}

for (const width of [1440, 375]) {
  test(`Home keeps the original list and a text link to discovery at ${width}px`, async t => {
    const { page, errors } = await open(t, { width });
    await page.goto(origin);
    await page.getByRole('link', { name: 'Mở Server Java Study', exact: true }).waitFor();
    await page.getByRole('link', { name: 'Mở Server Nhóm Học Tập', exact: true }).waitFor();
    assert.equal(await page.getByRole('textbox', { name: 'Tìm server của bạn', exact: true }).count(), 0);
    assert.equal(await page.getByRole('button', { name: /Khám phá server/ }).count(), 0);
    assert.equal(await page.locator('.main-primary-dock a[href="/servers/1"]').count(), 1);
    const link = page.getByRole('link', { name: 'Khám phá server', exact: true });
    assert.equal(await link.getAttribute('href'), '/discover');
    assert.equal(await link.locator('svg').count(), 0);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(artifacts, `home-${width}.png`), fullPage: true });
    await link.focus();
    await link.press('Enter');
    await page.waitForURL(url => url.pathname === '/discover');
    await page.getByRole('list', { name: 'Kết quả tìm server' }).waitFor();
    assert.deepEqual(errors, []);
  });

  test(`discovery exposes public results and joins once at ${width}px`, async t => {
    const pending = gate(t);
    const submitted = gate(t);
    let joins = 0;
    const { page, requests, errors } = await open(t, { width, handler: async (route, url, state) => {
      if (!url.pathname.endsWith('/3/join')) return false;
      joins++;
      submitted.resolve();
      await pending.promise;
      state.markJoined();
      await route.fulfill({ status: 204, body: '' });
      return true;
    } });
    await page.goto(`${origin}/discover`);
    await page.getByRole('list', { name: 'Kết quả tìm server' }).waitFor();
    assert.equal(await page.getByText('Nhóm Học Tập', { exact: true }).count(), 0);
    assert.equal(await page.locator('.discovery-results').getByText('Java Study', { exact: true }).count(), 0);
    assert.equal(await page.getByRole('button', { name: 'Mở server', exact: true }).count(), 0);
    await page.screenshot({ path: path.join(artifacts, `discovery-${width}.png`), fullPage: true });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    const join = page.locator('.discovery-results li').filter({ hasText: 'Java Community' }).getByRole('button');
    await join.click();
    await submitted.promise;
    assert.equal(joins, 1);
    assert.equal(await join.isDisabled(), true);
    const detailRequest = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/servers/3');
    pending.resolve();
    await page.waitForURL(url => url.pathname === '/servers/3');
    await detailRequest;
    assert.ok(await page.locator('.main-primary-dock a[href="/servers/3"]').count() > 0);
    assert.ok(requests.some(request => request.path === '/api/v1/servers/3'));
    await page.goto(`${origin}/discover`);
    await page.getByText('Chưa có server mới để khám phá', { exact: true }).waitFor();
    assert.equal(await page.locator('.discovery-results li').count(), 0);
    assert.deepEqual(errors, []);
  });
}

test('discovery debounces typing, clears search and ignores old results', async t => {
  const pending = gate(t);
  const { page, requests, errors } = await open(t, { handler: async (route, url) => {
    if (url.pathname !== '/api/v1/servers/discover' || url.searchParams.get('q') !== 'old') return false;
    await pending.promise;
    await route.fulfill({ json: { data: { items: [{ id: 99, name: 'Obsolete result', joined: false }], totalElements: 1 } } });
    return true;
  } });
  await page.goto(`${origin}/discover`);
  const search = page.getByRole('textbox', { name: 'Tìm server công khai' });
  await page.getByRole('list', { name: 'Kết quả tìm server' }).waitFor();
  const oldRequest = page.waitForRequest(request => new URL(request.url()).searchParams.get('q') === 'old');
  await search.fill('old');
  await oldRequest;
  await search.fill('J');
  await search.fill('Ja');
  await search.fill('Java');
  await page.getByRole('list', { name: 'Kết quả tìm server' }).waitFor();
  assert.equal(requests.some(request => ['J', 'Ja'].includes(request.q)), false);
  pending.resolve();
  await search.fill('missing');
  await page.getByText('Không tìm thấy server phù hợp', { exact: true }).waitFor();
  assert.equal(await page.getByText('Obsolete result', { exact: true }).count(), 0);
  await page.getByRole('button', { name: 'Xóa tìm kiếm' }).click();
  await page.getByRole('list', { name: 'Kết quả tìm server' }).waitFor();
  assert.deepEqual(errors, []);
});

test('discovery pagination sends the next page and resets it on a new query', async t => {
  const { page, requests } = await open(t, { handler: async (route, url) => {
    if (url.pathname !== '/api/v1/servers/discover') return false;
    const index = Number(url.searchParams.get('page'));
    await route.fulfill({ json: { data: { items: [{ id: index + 20, name: `Page ${index}`, joined: false }],
      totalElements: 21, totalPages: 2, page: index, size: 20 } } });
    return true;
  } });
  await page.goto(`${origin}/discover`);
  await page.getByText('Page 0', { exact: true }).waitFor();
  await page.locator('.ant-pagination-item-2').click();
  await page.getByText('Page 1', { exact: true }).waitFor();
  await page.getByRole('textbox', { name: 'Tìm server công khai' }).fill('new');
  await page.getByText('Page 0', { exact: true }).waitFor();
  assert.ok(requests.some(request => request.page === '1'));
  assert.ok(requests.some(request => request.q === 'new' && request.page === '0'));
});

test('discovery distinguishes loading, network failure with retry, and no public servers', async t => {
  const pending = gate(t);
  let attempts = 0;
  const { page } = await open(t, { handler: async (route, url) => {
    if (url.pathname !== '/api/v1/servers/discover') return false;
    attempts++;
    if (attempts === 1) {
      await pending.promise;
      await route.fulfill({ status: 503, json: { detail: 'PRIVATE_INTERNAL_MARKER' } });
    } else await route.fulfill({ json: { data: { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 } } });
    return true;
  } });
  await page.goto(`${origin}/discover`);
  await page.getByRole('status', { name: 'Đang tìm server' }).waitFor();
  pending.resolve();
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await page.getByText('Chưa có server mới để khám phá', { exact: true }).waitFor();
  assert.equal(await page.getByText('PRIVATE_INTERNAL_MARKER').count(), 0);
});

for (const status of [403, 404, 500]) {
  test(`failed join ${status} stays on discovery and permits retry`, async t => {
    let joins = 0;
    const { page } = await open(t, { handler: async (route, url) => {
      if (!url.pathname.endsWith('/3/join')) return false;
      joins++;
      await route.fulfill({ status, json: { detail: 'PRIVATE_INTERNAL_MARKER' } });
      return true;
    } });
    await page.goto(`${origin}/discover`);
    await page.getByRole('button', { name: 'Tham gia', exact: true }).click();
    await page.getByRole('alert').waitFor();
    assert.equal(new URL(page.url()).pathname, '/discover');
    assert.equal(await page.getByText('PRIVATE_INTERNAL_MARKER').count(), 0);
    await page.getByRole('button', { name: 'Tham gia', exact: true }).click();
    await page.getByRole('alert').waitFor();
    assert.equal(joins, 2);
  });
}

test('late successful join updates the dock without taking navigation from Home', async t => {
  const pending = gate(t);
  const { page } = await open(t, { handler: async (route, url, state) => {
    if (!url.pathname.endsWith('/3/join')) return false;
    await pending.promise;
    state.markJoined();
    await route.fulfill({ status: 204, body: '' });
    return true;
  } });
  await page.goto(`${origin}/discover`);
  const joinRequest = page.waitForRequest(request => request.url().endsWith('/3/join'));
  await page.getByRole('button', { name: 'Tham gia', exact: true }).click();
  await joinRequest;
  await page.getByRole('link', { name: 'Server của bạn', exact: true }).click();
  await page.waitForURL(url => url.pathname === '/');
  pending.resolve();
  await page.getByRole('link', { name: 'Mở Server Java Community', exact: true }).waitFor();
  assert.equal(new URL(page.url()).pathname, '/');
});

test('Home preserves list failure and genuinely empty membership states with a discovery link', async t => {
  for (const listFailure of [false, true]) {
    const { page } = await open(t, { personal: false, listFailure });
    await page.goto(origin);
    await page.getByText(listFailure ? 'Chưa tải được danh sách Server' : 'Bạn chưa có Server nào', { exact: true }).waitFor();
    assert.equal(await page.getByRole('textbox', { name: 'Tìm server của bạn' }).count(), 0);
    assert.equal(await page.getByRole('link', { name: 'Khám phá server', exact: true }).isVisible(), true);
  }
});

test('server creation defaults private and submits an explicit public choice', async t => {
  const { page, creates } = await open(t);
  await page.goto(origin);
  await page.getByRole('button', { name: /Tạo Không Gian \(Server\) Mới/ }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByText('Riêng tư — tham gia bằng mã mời', { exact: true }).waitFor();
  await dialog.locator('.ant-select-selector').click();
  await page.getByText('Công khai — cho phép tìm và tham gia', { exact: true }).click();
  await dialog.getByRole('button', { name: 'Khởi Tạo Không Gian', exact: true }).click();
  await page.waitForURL(url => url.pathname === '/servers/4');
  assert.equal(creates[0].visibility, 'PUBLIC');
});

test('Owner visibility draft supports discard and explicit save', async t => {
  const { page, updates } = await open(t);
  await page.goto(`${origin}/servers/1`);
  await page.getByRole('button', { name: 'Cài đặt Server', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Cài đặt Server', exact: true });
  const select = dialog.locator('.ant-select-selector');
  await select.click();
  await page.getByText('Riêng tư — tham gia bằng mã mời', { exact: true }).click();
  await dialog.getByRole('button', { name: 'Hủy thay đổi', exact: true }).click();
  assert.equal(updates.length, 0);
  await dialog.getByText('Công khai — cho phép tìm và tham gia', { exact: true }).waitFor();
  await select.click();
  await page.getByText('Riêng tư — tham gia bằng mã mời', { exact: true }).click();
  await dialog.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await page.getByText('Đã lưu thay đổi Server.', { exact: true }).waitFor();
  assert.equal(updates[0].visibility, 'PRIVATE');
});

test('discovery route requires authentication', async t => {
  const { page, requests } = await open(t, { authenticated: false });
  await page.goto(`${origin}/discover`);
  await page.waitForURL(url => url.pathname === '/login');
  assert.equal(requests.some(request => request.path === '/api/v1/servers/discover'), false);
});
