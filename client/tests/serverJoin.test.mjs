import test from 'node:test';
import assert from 'node:assert/strict';
import {
  validateServerId,
  parseServerId,
  validateJoinInput,
  parseJoinInput,
  getJoinErrorMessage,
} from '../src/utils/serverJoin.js';

test('validateServerId rejects null, undefined, empty, and whitespace-only values', () => {
  for (const val of [null, undefined, '', '   ', '\t\n']) {
    const error = validateServerId(val);
    assert.equal(error, 'Vui lòng nhập ID của Server.');
  }
});

test('validateServerId rejects negative numbers, zero, floats, and non-numeric strings', () => {
  for (const val of ['0', '-1', '-100', '12.34', 'abc', '12a', 'server-1']) {
    const error = validateServerId(val);
    assert.equal(error, 'Mã Server ID phải là số nguyên dương hợp lệ.');
  }
});

test('validateServerId accepts valid positive integers', () => {
  for (const val of ['1', '10', '999999', 1, 42, '  15  ']) {
    const error = validateServerId(val);
    assert.equal(error, null);
  }
});

test('parseServerId correctly parses valid values and returns null for invalid', () => {
  assert.equal(parseServerId('10'), 10);
  assert.equal(parseServerId('  42  '), 42);
  assert.equal(parseServerId('abc'), null);
  assert.equal(parseServerId(''), null);
  assert.equal(parseServerId(null), null);
});

test('validateJoinInput rejects empty and whitespace values', () => {
  for (const val of [null, undefined, '', '   ', '\t\n']) {
    const error = validateJoinInput(val);
    assert.equal(error, 'Vui lòng nhập Mã mời hoặc Server ID.');
  }
});

test('validateJoinInput accepts valid server IDs and invite codes / invite URLs', () => {
  assert.equal(validateJoinInput('10'), null);
  assert.equal(validateJoinInput('aB9xK2Lm'), null);
  assert.equal(validateJoinInput('https://denhub.app/invite/aB9xK2Lm'), null);
  assert.equal(validateJoinInput('/invite/aB9xK2Lm'), null);
});

test('validateJoinInput rejects invalid characters or malformed input', () => {
  assert.equal(
    validateJoinInput('!@#$%'),
    'Mã mời hoặc Server ID không hợp lệ. Vui lòng nhập mã mời (ví dụ: aB9xK2Lm) hoặc mã số Server.'
  );
  assert.equal(
    validateJoinInput('abc'),
    'Mã mời hoặc Server ID không hợp lệ. Vui lòng nhập mã mời (ví dụ: aB9xK2Lm) hoặc mã số Server.'
  );
});

test('parseJoinInput correctly classifies serverId vs inviteCode', () => {
  assert.deepEqual(parseJoinInput('10'), { type: 'serverId', value: 10 });
  assert.deepEqual(parseJoinInput('  42  '), { type: 'serverId', value: 42 });
  assert.deepEqual(parseJoinInput('aB9xK2Lm'), { type: 'inviteCode', value: 'aB9xK2Lm' });
  assert.deepEqual(parseJoinInput('https://denhub.app/invite/aB9xK2Lm'), { type: 'inviteCode', value: 'aB9xK2Lm' });
  assert.deepEqual(parseJoinInput('/invite/xY7zAb12'), { type: 'inviteCode', value: 'xY7zAb12' });
  assert.equal(parseJoinInput(''), null);
  assert.equal(parseJoinInput(null), null);
});

test('getJoinErrorMessage maps alreadymember error correctly', () => {
  const err1 = { response: { status: 400, data: { errorKey: 'alreadymember' } } };
  assert.equal(getJoinErrorMessage(err1), 'Bạn đã là thành viên của Server này rồi!');

  const err2 = { response: { status: 400, data: { message: 'User is already a member of this server' } } };
  assert.equal(getJoinErrorMessage(err2), 'Bạn đã là thành viên của Server này rồi!');
});

test('getJoinErrorMessage maps invite specific errors correctly', () => {
  const errNotFound = { response: { status: 404, data: { errorKey: 'invitenotfound' } } };
  assert.equal(getJoinErrorMessage(errNotFound), 'Mã mời không tồn tại hoặc không chính xác!');

  const errExpired = { response: { status: 400, data: { errorKey: 'inviteexpired' } } };
  assert.equal(getJoinErrorMessage(errExpired), 'Mã mời này đã hết hạn sử dụng!');

  const errRevoked = { response: { status: 400, data: { errorKey: 'inviterevoked' } } };
  assert.equal(getJoinErrorMessage(errRevoked), 'Mã mời này đã bị vô hiệu hóa!');
});

test('getJoinErrorMessage maps servernotfound error correctly', () => {
  const err1 = { response: { status: 400, data: { errorKey: 'servernotfound' } } };
  assert.equal(getJoinErrorMessage(err1), 'Không tìm thấy Server với thông tin này. Vui lòng kiểm tra lại!');

  const err2 = { response: { status: 404, data: {} } };
  assert.equal(getJoinErrorMessage(err2), 'Không tìm thấy Server với thông tin này. Vui lòng kiểm tra lại!');
});

test('getJoinErrorMessage maps authentication and permission errors', () => {
  const err401 = { response: { status: 401, data: { errorKey: 'notloggedin' } } };
  assert.equal(getJoinErrorMessage(err401), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  const err403 = { response: { status: 403, data: {} } };
  assert.equal(getJoinErrorMessage(err403), 'Bạn không có quyền tham gia Server này.');
});

test('getJoinErrorMessage maps network and fallback errors safely', () => {
  const netErr = { code: 'ERR_NETWORK' };
  assert.equal(getJoinErrorMessage(netErr), 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!');

  const emptyErr = {};
  assert.equal(getJoinErrorMessage(emptyErr), 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!');

  const fallbackErr = { response: { status: 500, data: {} } };
  assert.equal(getJoinErrorMessage(fallbackErr), 'Không thể tham gia Server. Vui lòng thử lại!');
});
