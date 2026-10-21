import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import os from 'node:os';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';

const { chromium } = createRequire(import.meta.url)('playwright');
const root = fileURLToPath(new URL('../', import.meta.url));
const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jrZkAAAAASUVORK5CYII=', 'base64');
const artifacts = process.env.SERVER_SETTINGS_ARTIFACT_DIR || path.join(os.tmpdir(), 'denhub-server-settings');
let vite, browser, origin;

before(async () => {
  vite = await createServer({ root, configFile: false, plugins: [react()], server: { host: '127.0.0.1', port: 0 } });
  await vite.listen();
  origin = `http://127.0.0.1:${vite.httpServer.address().port}`;
  browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge', headless: true });
  await mkdir(artifacts, { recursive: true });
});
after(async () => { await browser?.close(); await vite?.close(); });

async function openServer(t, { owner = true, width = 1440, onUpdate, onUpload, onDelete, onList } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
  t.after(() => context.close());
  context.setDefaultTimeout(12000);
  context.setDefaultNavigationTimeout(30000);
  await context.addInitScript(() => {
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, sub: 'ui-test@example.invalid' }));
    localStorage.setItem('denhub_access_token', `fixture.${payload}.fixture`);
    localStorage.setItem('denhub_user', JSON.stringify({ id: 100, displayName: 'UI Test', email: 'ui-test@example.invalid' }));
  });
  await context.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  await context.route('https://images.unsplash.com/**', route => route.fulfill({ contentType: 'image/png', body: image }));
  const server = { id: 1, ownerId: owner ? 100 : 200, name: 'Nhóm học tập', iconUrl: null };
  const otherServer = { id: 2, ownerId: 100, name: 'Server khác', iconUrl: null };
  const updates = [], uploads = [], errors = [], deletes = [];
  let deleted = false;
  await context.route(/^https?:\/\/[^/]+\/api\//, async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'DELETE') {
      assert.equal(url.pathname, '/api/v1/servers/1');
      deletes.push(request);
      if (onDelete) return onDelete(route, deletes);
      deleted = true;
      return route.fulfill({ status: 204, body: '' });
    }
    if (url.pathname.includes('/files/') && request.method() === 'GET') {
      return route.fulfill({ contentType: 'image/png', body: image });
    }
    if (url.pathname === '/api/v1/files/upload') {
      uploads.push(request);
      if (onUpload) return onUpload(route, uploads);
      return route.fulfill({ status: 201, json: { data: { url: 'http://localhost:8080/api/v1/files/servers/new.png' } } });
    }
    if (request.method() === 'PUT') {
      assert.equal(url.pathname, '/api/v1/servers/1');
      const body = request.postDataJSON();
      updates.push(body);
      if (onUpdate) return onUpdate(route, body);
      Object.assign(server, body);
      return route.fulfill({ json: { statusCode: 200, error: null, data: server } });
    }
    if (url.pathname === '/api/v1/servers') {
      if (onList) return onList(route, { server, otherServer, deleted });
      return route.fulfill({ json: { data: deleted ? [otherServer] : [server, otherServer] } });
    }
    if (url.pathname === '/api/v1/servers/1') return route.fulfill({ json: { data: server } });
    if (url.pathname === '/api/v1/servers/2') return route.fulfill({ json: { data: otherServer } });
    return route.fulfill({ json: { data: [] } });
  });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${origin}/servers/1`, { waitUntil: 'domcontentloaded' });
  const settingsButton = page.getByRole('button', { name: 'Cài đặt Server', exact: true });
  if (owner) {
    if (width < 1024) await page.getByRole('button', { name: 'Mở danh sách kênh' }).click();
    await settingsButton.click();
    await page.getByRole('dialog', { name: 'Cài đặt Server', exact: true }).waitFor();
  }
  return { page, settingsButton, updates, uploads, deletes, errors, dialog: page.getByRole('dialog', { name: 'Cài đặt Server', exact: true }) };
}

async function openDelete(page, dialog) {
  await page.clock.install();
  await page.clock.runFor(400);
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 100));
  await dialog.getByRole('button', { name: 'Xóa Server', exact: true }).click();
  const confirmation = page.getByRole('dialog', { name: 'Xóa vĩnh viễn Server?', exact: true });
  await confirmation.waitFor();
  await page.clock.runFor(350);
  return confirmation;
}

test('deletion warns about data loss and requires the full three-second countdown', async t => {
  const { page, dialog, deletes, errors } = await openServer(t);
  const confirmation = await openDelete(page, dialog);
  assert.match(await confirmation.textContent(), /Nhóm học tập/);
  assert.match(await confirmation.textContent(), /không thể hoàn tác/i);
  assert.match(await confirmation.textContent(), /#general/);
  await page.screenshot({ animations: 'disabled', path: path.join(artifacts, 'delete-server-desktop.png') });
  const confirm = confirmation.getByRole('button', { name: /Xác nhận xóa/ });
  assert.equal(await confirm.isDisabled(), true);
  await confirmation.press('Enter');
  assert.equal(deletes.length, 0);
  await page.clock.fastForward(2500);
  assert.equal(await confirm.isDisabled(), true);
  await page.clock.fastForward(300);
  await confirm.waitFor();
  await confirm.click();
  await page.waitForURL(url => url.pathname === '/');
  assert.equal(deletes.length, 1);
  assert.equal(await page.locator('a[href="/servers/1"]').count(), 0);
  assert.ok(await page.locator('a[href="/servers/2"]').count() > 0);
  assert.deepEqual(errors, []);
});

test('cancel never deletes and reopening restarts the countdown', async t => {
  const { page, dialog, deletes } = await openServer(t);
  const confirmation = await openDelete(page, dialog);
  await page.clock.fastForward(3100);
  await confirmation.getByRole('button', { name: 'Hủy', exact: true }).click();
  await page.clock.runFor(350);
  await confirmation.waitFor({ state: 'hidden' });
  await dialog.getByRole('button', { name: 'Xóa Server', exact: true }).click();
  await confirmation.waitFor();
  assert.equal(await confirmation.getByRole('button', { name: /Xác nhận xóa/ }).isDisabled(), true);
  assert.equal(deletes.length, 0);
});

test('pending deletion blocks duplicate confirmation and closing', async t => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  t.after(() => release());
  const { page, dialog, deletes } = await openServer(t, { onDelete: async route => {
    await pending;
    await route.fulfill({ status: 204, body: '' });
  } });
  const confirmation = await openDelete(page, dialog);
  await page.clock.fastForward(3100);
  const confirm = confirmation.getByRole('button', { name: /Xác nhận xóa/ });
  const deletingRequest = page.waitForRequest(request => request.method() === 'DELETE');
  await confirm.click();
  await deletingRequest;
  assert.equal(await confirm.isDisabled(), true);
  assert.equal(await confirmation.getByRole('button', { name: 'Hủy', exact: true }).isDisabled(), true);
  await confirmation.press('Escape');
  assert.equal(await confirmation.isVisible(), true);
  assert.equal(deletes.length, 1);
  release();
  await page.waitForURL(url => url.pathname === '/');
});

for (const status of [403, 404, 409, 500]) {
  test(`delete ${status} keeps the server and shows a safe error`, async t => {
    const { page, dialog, deletes } = await openServer(t, { onDelete: route => route.fulfill({
      status, json: { status, detail: 'SQL_INTERNAL_CREDENTIAL_MARKER' },
    }) });
    const confirmation = await openDelete(page, dialog);
    await page.clock.fastForward(3100);
    await confirmation.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
    const expected = { 403: 'Chỉ chủ sở hữu đang có quyền mới được xóa Server này.',
      404: 'Server không còn tồn tại. Hãy đóng hộp thoại và tải lại danh sách.',
      409: 'Dữ liệu tin nhắn có liên kết với hội thoại riêng nên chưa thể xóa an toàn. Vui lòng liên hệ quản trị viên.',
      500: 'Không thể xác nhận Server đã được xóa. Vui lòng tải lại danh sách hoặc thử lại.' };
    await confirmation.getByText(expected[status], { exact: true }).waitFor();
    assert.equal(deletes.length, 1);
    assert.equal(new URL(page.url()).pathname, '/servers/1');
    assert.equal((await confirmation.textContent()).includes('SQL_INTERNAL_CREDENTIAL_MARKER'), false);
    assert.ok(await page.locator('a[href="/servers/1"]').count() > 0);
    await page.clock.runFor(350);
    assert.equal(await confirmation.getByRole('button', { name: /Xác nhận xóa/ }).isEnabled(), true);
  });
}

test('failed delete can be retried successfully', async t => {
  const { page, dialog, deletes } = await openServer(t, { onDelete: (route, calls) => calls.length === 1
    ? route.abort('failed') : route.fulfill({ status: 204, body: '' }) });
  const confirmation = await openDelete(page, dialog);
  await page.clock.fastForward(3100);
  await confirmation.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
  await confirmation.getByText('Không thể xác nhận Server đã được xóa. Vui lòng tải lại danh sách hoặc thử lại.', { exact: true }).waitFor();
  await page.clock.runFor(350);
  await confirmation.getByRole('button', { name: /Xác nhận xóa/ }).click();
  await page.waitForURL(url => url.pathname === '/');
  assert.equal(deletes.length, 2);
});

test('mobile owner can review warning and delete after countdown', async t => {
  const { page, dialog, errors } = await openServer(t, { width: 375 });
  const confirmation = await openDelete(page, dialog);
  await page.screenshot({ animations: 'disabled', path: path.join(artifacts, 'delete-server-mobile.png') });
  await page.clock.fastForward(3100);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await confirmation.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
  await page.waitForURL(url => url.pathname === '/');
  assert.deepEqual(errors, []);
});

test('delete completion for the previous server updates the dock without redirecting the current server', async t => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  t.after(() => release());
  const { page, dialog, deletes, errors } = await openServer(t, { onDelete: async route => {
    await pending;
    await route.fulfill({ status: 204, body: '' });
  } });
  const confirmation = await openDelete(page, dialog);
  await page.clock.fastForward(3100);
  const deletingRequest = page.waitForRequest(request => request.method() === 'DELETE');
  await confirmation.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
  await deletingRequest;
  // A route change can also come from browser history, outside the modal's controls.
  await page.locator('a[href="/servers/2"]').first().evaluate(link => link.click());
  await page.waitForURL(url => url.pathname === '/servers/2');
  const deleteResponse = page.waitForResponse(response => response.request().method() === 'DELETE');
  release();
  await deleteResponse;
  await page.locator('a[href="/servers/1"]').waitFor({ state: 'detached' });
  assert.equal(new URL(page.url()).pathname, '/servers/2');
  assert.match(await page.locator('.group-sidebar__identity strong').textContent(), /Server khác/);
  assert.equal(deletes.length, 1);
  assert.deepEqual(errors, []);
});

test('a pending old server list cannot restore the deleted server or hide the remaining servers', async t => {
  let release;
  const oldList = new Promise(resolve => { release = resolve; });
  t.after(() => release());
  let listCalls = 0;
  const { page, dialog, errors } = await openServer(t, { onList: async (route, { server, otherServer }) => {
    if (++listCalls === 1) {
      await oldList;
      return route.fulfill({ json: { data: [server, otherServer] } });
    }
    return route.fulfill({ json: { data: [otherServer] } });
  } });
  const confirmation = await openDelete(page, dialog);
  await page.clock.fastForward(3100);
  await confirmation.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
  await page.waitForURL(url => url.pathname === '/');
  await page.locator('a[href="/servers/2"]').first().waitFor();
  const staleResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/servers'));
  release();
  await staleResponse;
  await page.clock.fastForward(100);
  assert.equal(await page.locator('a[href="/servers/1"]').count(), 0);
  assert.ok(await page.locator('a[href="/servers/2"]').count() > 0);
  assert.equal(listCalls, 2);
  assert.deepEqual(errors, []);
});

test('owner edits the name and synchronizes the server detail and dock', async t => {
  const { page, dialog, updates, errors } = await openServer(t);
  await dialog.getByLabel('Tên Server', { exact: true }).fill('  Nhóm mới  ');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await page.getByText('Đã lưu thay đổi Server.', { exact: true }).waitFor();
  assert.deepEqual(updates, [{ name: 'Nhóm mới', iconUrl: null }]);
  await dialog.getByRole('button', { name: 'Close', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(await page.locator('.group-sidebar__identity strong').textContent(), 'Nhóm mới');
  assert.equal(await page.locator('a[href="/servers/1"]').getAttribute('aria-label'), 'Mở nhóm Nhóm mới');
  assert.deepEqual(errors, []);
});

test('member has no server settings entry', async t => {
  const { settingsButton, updates, errors } = await openServer(t, { owner: false });
  assert.equal(await settingsButton.count(), 0);
  assert.deepEqual(updates, []);
  assert.deepEqual(errors, []);
});

test('cancel restores the original values without calling update', async t => {
  const { dialog, updates } = await openServer(t);
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Chưa lưu');
  await dialog.getByRole('button', { name: 'Hủy thay đổi' }).click();
  assert.equal(await dialog.getByLabel('Tên Server', { exact: true }).inputValue(), 'Nhóm học tập');
  assert.equal(await dialog.getByRole('button', { name: 'Lưu thay đổi' }).count(), 0);
  assert.deepEqual(updates, []);
});

test('closing a dirty form requires discard confirmation', async t => {
  const { page, dialog, updates } = await openServer(t);
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Chưa lưu');
  await dialog.getByRole('button', { name: 'Close', exact: true }).click();
  await page.getByRole('button', { name: 'Tiếp tục chỉnh sửa' }).click();
  assert.equal(await dialog.getByLabel('Tên Server', { exact: true }).inputValue(), 'Chưa lưu');
  await dialog.getByRole('button', { name: 'Close', exact: true }).click();
  await page.getByRole('button', { name: 'Bỏ thay đổi', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.deepEqual(updates, []);
});

test('blank Unicode name is rejected and the form keeps its draft on API failure', async t => {
  const { dialog, updates } = await openServer(t, { onUpdate: route => route.fulfill({ status: 500, json: { detail: 'Thử lại sau' } }) });
  await dialog.getByLabel('Tên Server', { exact: true }).fill('\u00a0\u2003');
  await dialog.getByLabel('Tên Server', { exact: true }).press('Enter');
  await dialog.getByText('Vui lòng nhập tên Server.', { exact: true }).waitFor();
  assert.deepEqual(updates, []);
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Giữ bản nháp');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await dialog.getByRole('alert').getByText('Thử lại sau').waitFor();
  assert.equal(await dialog.getByLabel('Tên Server', { exact: true }).inputValue(), 'Giữ bản nháp');
});

test('pending save sends one request and disables edits', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const { dialog, updates } = await openServer(t, { onUpdate: async (route, body) => {
    await gate; await route.fulfill({ json: { data: { id: 1, ownerId: 100, ...body } } });
  } });
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Đang lưu');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await dialog.getByLabel('Tên Server', { exact: true }).waitFor();
  assert.equal(await dialog.getByLabel('Tên Server', { exact: true }).isDisabled(), true);
  assert.equal(await dialog.getByRole('button', { name: 'Hủy thay đổi' }).isDisabled(), true);
  assert.equal(updates.length, 1);
  release();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).waitFor({ state: 'hidden' });
});

test('upload blocks save, then saves the returned URL and supports icon removal', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const url = 'http://localhost:8080/api/v1/files/servers/new.png';
  const { dialog, updates } = await openServer(t, { onUpload: async route => {
    await gate; await route.fulfill({ status: 201, json: { data: { url } } });
  } });
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Có ảnh');
  await dialog.locator('input[type=file]').setInputFiles({ name: 'icon.png', mimeType: 'image/png', buffer: image });
  await dialog.getByText('Đang tải ảnh lên…', { exact: true }).waitFor();
  assert.equal(await dialog.getByRole('button', { name: 'Lưu thay đổi' }).isDisabled(), true);
  await dialog.getByLabel('Tên Server', { exact: true }).press('Enter');
  assert.deepEqual(updates, []);
  release();
  await dialog.locator('.server-settings__image img').waitFor();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).waitFor({ state: 'hidden' });
  assert.deepEqual(updates, [{ name: 'Có ảnh', iconUrl: url }]);
  await dialog.getByRole('button', { name: 'Gỡ ảnh', exact: true }).click();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).waitFor({ state: 'hidden' });
  assert.deepEqual(updates[1], { name: 'Có ảnh', iconUrl: null });
});

test('discarding an upload prevents its late response from changing a reopened form', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const { page, dialog, updates } = await openServer(t, { onUpload: async route => {
    await gate; await route.fulfill({ status: 201, json: { data: { url: 'http://localhost:8080/api/v1/files/servers/stale.png' } } });
  } });
  await dialog.locator('input[type=file]').setInputFiles({ name: 'icon.png', mimeType: 'image/png', buffer: image });
  await dialog.getByText('Đang tải ảnh lên…', { exact: true }).waitFor();
  await dialog.getByRole('button', { name: 'Close', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: 'Cài đặt Server', exact: true }).click();
  await dialog.waitFor();
  release();
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Không ảnh cũ');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).waitFor({ state: 'hidden' });
  assert.deepEqual(updates, [{ name: 'Không ảnh cũ', iconUrl: null }]);
});

for (const width of [320, 375, 768, 1440]) {
  test(`settings works at viewport ${width} without horizontal overflow`, async t => {
    const { page, dialog, errors } = await openServer(t, { width });
    assert.equal(await dialog.locator('input[type=file]').isVisible(), false);
    await dialog.getByLabel('Tên Server', { exact: true }).fill('Nhóm thiết kế DenHub');
    const overflow = await dialog.evaluate(element => element.scrollWidth > element.clientWidth + 1);
    assert.equal(overflow, false);
    await dialog.getByRole('button', { name: 'Lưu thay đổi' }).scrollIntoViewIfNeeded();
    if (width === 375 || width === 1440) await page.screenshot({ path: path.join(artifacts, `settings-${width}.png`), fullPage: true });
    assert.deepEqual(errors, []);
  });
}

for (const status of [403, 404]) {
  test(`update ${status} leaves the form open with a useful error`, async t => {
    const { dialog } = await openServer(t, { onUpdate: route => route.fulfill({ status, json: { status } }) });
    await dialog.getByLabel('Tên Server', { exact: true }).fill('Giữ bản nháp');
    await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
    await dialog.getByRole('alert').getByText(status === 403
      ? 'Bạn không còn quyền chỉnh sửa Server này.' : 'Server không còn tồn tại.').waitFor();
    assert.equal(await dialog.getByLabel('Tên Server', { exact: true }).inputValue(), 'Giữ bản nháp');
  });
}

test('invalid or oversized files do not start an upload', async t => {
  const { dialog, uploads } = await openServer(t);
  await dialog.locator('input[type=file]').setInputFiles({ name: 'icon.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') });
  await dialog.getByRole('alert').getByText('Chọn ảnh JPG, PNG, WebP hoặc GIF.').waitFor();
  await dialog.locator('input[type=file]').setInputFiles({ name: 'big.png', mimeType: 'image/png', buffer: Buffer.alloc(5 * 1024 * 1024 + 1) });
  await dialog.getByRole('alert').getByText('Kích thước ảnh tối đa 5 MB.').waitFor();
  assert.deepEqual(uploads, []);
});

test('a save response for the previous server cannot overwrite the current server', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const { page, dialog } = await openServer(t, { onUpdate: async (route, body) => {
    await gate;
    await route.fulfill({ json: { data: { id: 1, ownerId: 100, ...body } } });
  } });
  await dialog.getByLabel('Tên Server', { exact: true }).fill('Bản cập nhật cũ');
  const pending = page.waitForRequest(request => request.method() === 'PUT');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await pending;
  await page.evaluate(() => {
    history.pushState(null, '', '/servers/2');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await page.locator('.group-sidebar__identity strong').filter({ hasText: 'Server khác' }).waitFor();
  const finished = page.waitForResponse(response => response.request().method() === 'PUT');
  release();
  await (await finished).finished();
  assert.equal(await page.locator('.group-sidebar__identity strong').textContent(), 'Server khác');
  assert.equal(await dialog.count(), 0);
});

test('a newer icon upload wins when the older upload finishes last', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const { page, dialog, updates } = await openServer(t, { onUpload: async (route, uploads) => {
    const old = uploads.length === 1;
    if (old) await gate;
    await route.fulfill({ status: 201, json: { data: { url: `http://localhost:8080/api/v1/files/servers/${old ? 'old' : 'new'}.png` } } });
  } });
  const input = dialog.locator('input[type=file]');
  await input.setInputFiles({ name: 'old.png', mimeType: 'image/png', buffer: image });
  await dialog.getByText('Đang tải ảnh lên…', { exact: true }).waitFor();
  await input.setInputFiles({ name: 'new.png', mimeType: 'image/png', buffer: image });
  await dialog.locator('.server-settings__image img[src$="new.png"]').waitFor();
  const finished = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/files/upload'));
  release();
  await (await finished).finished();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).waitFor({ state: 'hidden' });
  assert.equal(updates[0].iconUrl, 'http://localhost:8080/api/v1/files/servers/new.png');
});
