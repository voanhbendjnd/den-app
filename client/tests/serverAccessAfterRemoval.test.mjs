import test from 'node:test';
import assert from 'node:assert/strict';
import {
  isMemberAllowedAccess,
  getRemovedMemberAccessErrorMessage,
  filterActiveServersAfterRemoval,
  resolveRemovalRedirectPath,
} from '../src/utils/serverMember.js';

test('isMemberAllowedAccess: owner always has access even if not in members list', () => {
  const server = { id: 10, ownerId: 100 };
  const ownerUser = { id: 100, name: 'Server Owner' };

  assert.equal(isMemberAllowedAccess(server, ownerUser), true);
  assert.equal(isMemberAllowedAccess(server, ownerUser, []), true);
  assert.equal(isMemberAllowedAccess(server, ownerUser, [{ userId: 200 }]), true);
});

test('isMemberAllowedAccess: active member in members list is granted access', () => {
  const server = { id: 10, ownerId: 100 };
  const activeMember = { id: 200, name: 'Active Member' };
  const members = [
    { userId: 100, role: 'OWNER' },
    { userId: 200, role: 'MEMBER' },
  ];

  assert.equal(isMemberAllowedAccess(server, activeMember, members), true);
});

test('isMemberAllowedAccess: kicked / removed member is blocked from access', () => {
  const server = { id: 10, ownerId: 100 };
  const kickedMember = { id: 300, name: 'Kicked Member' };
  // Target member 300 was removed, so only owner 100 remains in members list
  const membersAfterKick = [
    { userId: 100, role: 'OWNER' },
  ];

  assert.equal(isMemberAllowedAccess(server, kickedMember, membersAfterKick), false);
  assert.equal(isMemberAllowedAccess(server, kickedMember, []), false);
  assert.equal(isMemberAllowedAccess(server, kickedMember, null), false);
});

test('isMemberAllowedAccess: invalid inputs return false', () => {
  assert.equal(isMemberAllowedAccess(null, { id: 1 }), false);
  assert.equal(isMemberAllowedAccess({ id: 1 }, null), false);
  assert.equal(isMemberAllowedAccess(null, null), false);
  assert.equal(isMemberAllowedAccess({}, {}), false);
});

test('getRemovedMemberAccessErrorMessage: returns appropriate messages for HTTP statuses', () => {
  // 403 Forbidden
  const err403 = { response: { status: 403, data: { message: 'Access Denied' } } };
  assert.equal(
    getRemovedMemberAccessErrorMessage(err403),
    'Bạn không còn là thành viên của Server này hoặc đã bị loại.'
  );

  // 400 Bad Request with forbidden / permission error
  const err400Forbidden = {
    response: {
      status: 400,
      data: { errorKey: 'forbidden', message: 'You do not have permission to access channels in this server' },
    },
  };
  assert.equal(
    getRemovedMemberAccessErrorMessage(err400Forbidden),
    'Bạn không có quyền truy cập hoặc thực hiện thao tác trong Server này.'
  );

  // 404 Not Found
  const err404 = { response: { status: 404, data: { errorKey: 'servernotfound' } } };
  assert.equal(
    getRemovedMemberAccessErrorMessage(err404),
    'Server không tồn tại hoặc đã bị xóa.'
  );

  // 401 Unauthorized
  const err401 = { response: { status: 401 } };
  assert.equal(
    getRemovedMemberAccessErrorMessage(err401),
    'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!'
  );

  // Network & timeout
  const timeoutErr = { code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded' };
  assert.equal(
    getRemovedMemberAccessErrorMessage(timeoutErr),
    'Yêu cầu quá thời gian chờ. Vui lòng kiểm tra lại kết nối mạng!'
  );

  const netErr = { code: 'ERR_NETWORK' };
  assert.equal(
    getRemovedMemberAccessErrorMessage(netErr),
    'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!'
  );

  // Null fallback
  assert.equal(
    getRemovedMemberAccessErrorMessage(null),
    'Bạn không có quyền truy cập Server này.'
  );
});

test('filterActiveServersAfterRemoval: removes server by ID and handles edge cases', () => {
  const initial = [
    { id: 1, name: 'Alpha' },
    { id: 2, name: 'Beta' },
    { id: 3, name: 'Gamma' },
  ];

  const filtered = filterActiveServersAfterRemoval(initial, 2);
  assert.equal(filtered.length, 2);
  assert.equal(filtered.some((s) => s.id === 2), false);
  assert.equal(filtered.some((s) => s.id === 1), true);
  assert.equal(filtered.some((s) => s.id === 3), true);

  // String ID matching
  const filteredStr = filterActiveServersAfterRemoval(initial, '1');
  assert.equal(filteredStr.length, 2);
  assert.equal(filteredStr.some((s) => s.id === 1), false);

  // Edge cases
  assert.deepEqual(filterActiveServersAfterRemoval(null, 1), []);
  assert.deepEqual(filterActiveServersAfterRemoval(undefined, 1), []);
  assert.deepEqual(filterActiveServersAfterRemoval(initial, null), initial);
});

test('resolveRemovalRedirectPath: redirects to first server or fallback route', () => {
  const otherServers = [
    { id: 456, name: 'Other Server' },
    { id: 789, name: 'Another Server' },
  ];

  // User was in server 123 and was removed from 123 -> redirect to first available server
  assert.equal(resolveRemovalRedirectPath(123, 123, otherServers), '/servers/456');

  // String ID matching
  assert.equal(resolveRemovalRedirectPath('123', 123, otherServers), '/servers/456');

  // No other servers left -> redirect to /servers list
  assert.equal(resolveRemovalRedirectPath(123, 123, []), '/servers');
  assert.equal(resolveRemovalRedirectPath(123, 123, null), '/servers');

  // User was in server 999 and removed from server 123 -> no redirect needed (null)
  assert.equal(resolveRemovalRedirectPath(999, 123, otherServers), null);
});
