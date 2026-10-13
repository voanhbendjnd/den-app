import test from 'node:test';
import assert from 'node:assert/strict';
import { validateServerId, parseServerId, getJoinErrorMessage } from '../src/utils/serverJoin.js';

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

test('getJoinErrorMessage maps alreadymember error correctly', () => {
  const err1 = { response: { status: 400, data: { errorKey: 'alreadymember' } } };
  assert.equal(getJoinErrorMessage(err1), 'Bạn đã là thành viên của Server này rồi!');

  const err2 = { response: { status: 400, data: { message: 'User is already a member of this server' } } };
  assert.equal(getJoinErrorMessage(err2), 'Bạn đã là thành viên của Server này rồi!');
});

test('getJoinErrorMessage maps servernotfound error correctly', () => {
  const err1 = { response: { status: 400, data: { errorKey: 'servernotfound' } } };
  assert.equal(getJoinErrorMessage(err1), 'Không tìm thấy Server với ID này. Vui lòng kiểm tra lại!');

  const err2 = { response: { status: 404, data: {} } };
  assert.equal(getJoinErrorMessage(err2), 'Không tìm thấy Server với ID này. Vui lòng kiểm tra lại!');
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
