import test from 'node:test';
import assert from 'node:assert/strict';
import { getInviteErrorMessage, formatExpiresAt, copyToClipboard } from '../src/utils/serverInvite.js';

test('getInviteErrorMessage maps authentication and permission errors', () => {
  const err401 = { response: { status: 401, data: {} } };
  assert.equal(getInviteErrorMessage(err401), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  const err401Key = { response: { status: 400, data: { errorKey: 'notloggedin' } } };
  assert.equal(getInviteErrorMessage(err401Key), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  const err403 = { response: { status: 403, data: {} } };
  assert.equal(getInviteErrorMessage(err403), 'Chỉ chủ sở hữu Server mới có quyền tạo mã mời!');

  const err403Msg = { response: { status: 400, data: { message: 'Chỉ chủ sở hữu Server mới có quyền tạo mã mời' } } };
  assert.equal(getInviteErrorMessage(err403Msg), 'Chỉ chủ sở hữu Server mới có quyền tạo mã mời!');
});

test('getInviteErrorMessage maps server not found and invalid parameters', () => {
  const err404 = { response: { status: 404, data: {} } };
  assert.equal(getInviteErrorMessage(err404), 'Server không tồn tại hoặc đã bị xóa!');

  const errPast = { response: { status: 400, data: { message: 'Thời gian hết hạn không được ở trong quá khứ' } } };
  assert.equal(getInviteErrorMessage(errPast), 'Thời gian hết hạn của mã mời không được ở trong quá khứ!');

  const errId = { response: { status: 400, data: { message: 'Server ID không hợp lệ' } } };
  assert.equal(getInviteErrorMessage(errId), 'Mã Server không hợp lệ.');
});

test('getInviteErrorMessage maps network and fallback errors safely', () => {
  const netErr = { code: 'ERR_NETWORK' };
  assert.equal(getInviteErrorMessage(netErr), 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!');

  const noResponseErr = {};
  assert.equal(getInviteErrorMessage(noResponseErr), 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!');

  const nullErr = null;
  assert.equal(getInviteErrorMessage(nullErr), 'Không thể tạo mã mời. Vui lòng thử lại!');

  const fallback500 = { response: { status: 500, data: {} } };
  assert.equal(getInviteErrorMessage(fallback500), 'Không thể tạo mã mời. Vui lòng thử lại sau!');
});

test('formatExpiresAt handles null, undefined and valid dates', () => {
  assert.equal(formatExpiresAt(null), 'Vĩnh viễn (Không hết hạn)');
  assert.equal(formatExpiresAt(undefined), 'Vĩnh viễn (Không hết hạn)');
  assert.equal(formatExpiresAt(''), 'Vĩnh viễn (Không hết hạn)');
  assert.equal(formatExpiresAt('invalid-date-string'), 'Vĩnh viễn (Không hết hạn)');

  const formatted = formatExpiresAt('2030-12-25T15:30:00Z');
  assert.match(formatted, /ngày 25\/12\/2030/);
});

test('copyToClipboard rejects empty or non-string input', async () => {
  const res1 = await copyToClipboard(null);
  assert.equal(res1.success, false);
  assert.equal(res1.error, 'Không có nội dung để sao chép.');

  const res2 = await copyToClipboard('');
  assert.equal(res2.success, false);
});

test('copyToClipboard uses navigator.clipboard.writeText when available', async () => {
  let copiedText = null;
  const originalClipboard = navigator.clipboard;
  Object.defineProperty(navigator, 'clipboard', {
    value: {
      writeText: async (text) => {
        copiedText = text;
      },
    },
    configurable: true,
  });

  const res = await copyToClipboard('TEST-INVITE-CODE');
  assert.equal(res.success, true);
  assert.equal(copiedText, 'TEST-INVITE-CODE');

  Object.defineProperty(navigator, 'clipboard', {
    value: originalClipboard,
    configurable: true,
  });
});

test('copyToClipboard returns error when clipboard fails and no document fallback', async () => {
  const originalClipboard = navigator.clipboard;
  Object.defineProperty(navigator, 'clipboard', {
    value: {
      writeText: async () => {
        throw new Error('Permission denied');
      },
    },
    configurable: true,
  });

  const res = await copyToClipboard('TEST-FAIL');
  assert.equal(res.success, false);
  assert.match(res.error, /Không thể tự động sao chép/);

  Object.defineProperty(navigator, 'clipboard', {
    value: originalClipboard,
    configurable: true,
  });
});
