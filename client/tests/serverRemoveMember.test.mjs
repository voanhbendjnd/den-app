import test from 'node:test';
import assert from 'node:assert/strict';
import { canRemoveMember, getRemoveMemberErrorMessage } from '../src/utils/serverMember.js';

test('canRemoveMember allows Server Owner to remove regular members', () => {
  const server = { id: 10, name: 'Alpha Guild', ownerId: '100' };
  const ownerUser = { id: '100', username: 'alice' };
  const memberUser = { userId: '200', username: 'bob', role: 'MEMBER' };

  const result = canRemoveMember(server, ownerUser, memberUser);
  assert.equal(result.canRemove, true);
  assert.equal(result.reason, null);

  // Numeric ID comparison works seamlessly with string IDs
  const numServer = { id: 10, name: 'Alpha Guild', ownerId: 100 };
  const numResult = canRemoveMember(numServer, { id: 100 }, { userId: 200, role: 'MEMBER' });
  assert.equal(numResult.canRemove, true);
});

test('canRemoveMember rejects non-owners from removing members', () => {
  const server = { id: 10, name: 'Alpha Guild', ownerId: '100' };
  const regularUser = { id: '200', username: 'bob' };
  const targetMember = { userId: '300', username: 'charlie', role: 'MEMBER' };

  const result = canRemoveMember(server, regularUser, targetMember);
  assert.equal(result.canRemove, false);
  assert.match(result.reason, /Chỉ chủ sở hữu Server mới có quyền loại thành viên/);
});

test('canRemoveMember prevents Server Owner from removing themselves', () => {
  const server = { id: 10, name: 'Alpha Guild', ownerId: '100' };
  const ownerUser = { id: '100', username: 'alice' };
  const selfMember = { userId: '100', username: 'alice', role: 'OWNER' };

  const result = canRemoveMember(server, ownerUser, selfMember);
  assert.equal(result.canRemove, false);
  assert.match(result.reason, /Chủ sở hữu không thể tự loại mình khỏi Server/);
});

test('canRemoveMember prevents removing member marked as OWNER', () => {
  const server = { id: 10, name: 'Alpha Guild', ownerId: '100' };
  const ownerUser = { id: '100', username: 'alice' };
  const targetOwnerMember = { userId: '500', username: 'co-owner', role: 'OWNER' };

  const result = canRemoveMember(server, ownerUser, targetOwnerMember);
  assert.equal(result.canRemove, false);
  assert.match(result.reason, /Không thể loại chủ sở hữu khỏi Server/);
});

test('canRemoveMember handles null, undefined, and empty objects safely', () => {
  const validServer = { id: 10, ownerId: '100' };
  const validUser = { id: '100' };
  const validMember = { userId: '200' };

  assert.equal(canRemoveMember(null, validUser, validMember).canRemove, false);
  assert.equal(canRemoveMember(validServer, null, validMember).canRemove, false);
  assert.equal(canRemoveMember(validServer, validUser, null).canRemove, false);
});

test('getRemoveMemberErrorMessage maps HTTP 401, 403, and 404 appropriately', () => {
  const memberName = 'Bob';

  // 401 Unauthorized
  const err401 = { response: { status: 401, data: {} } };
  assert.equal(getRemoveMemberErrorMessage(err401, memberName), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  const errNotLoggedIn = { response: { status: 400, data: { errorKey: 'notloggedin' } } };
  assert.equal(getRemoveMemberErrorMessage(errNotLoggedIn, memberName), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  // 403 Forbidden
  const err403 = { response: { status: 403, data: {} } };
  assert.equal(getRemoveMemberErrorMessage(err403, memberName), 'Bạn không có quyền loại thành viên này khỏi Server.');

  // 404 Not Found
  const err404 = { response: { status: 404, data: {} } };
  assert.equal(getRemoveMemberErrorMessage(err404, memberName), 'Thành viên hoặc Server không tồn tại hoặc đã bị xóa trước đó.');
});

test('getRemoveMemberErrorMessage maps HTTP 400 business rule violations correctly', () => {
  const memberName = 'Charlie';

  // Attempting to remove Owner
  const errOwner = {
    response: {
      status: 400,
      data: {
        message: 'Không thể loại chủ sở hữu khỏi Server',
      },
    },
  };
  assert.equal(getRemoveMemberErrorMessage(errOwner, memberName), 'Không thể loại chủ sở hữu khỏi Server.');

  // Not a member
  const errNotMember = {
    response: {
      status: 400,
      data: {
        message: 'Thành viên không tồn tại trong Server',
      },
    },
  };
  assert.equal(getRemoveMemberErrorMessage(errNotMember, memberName), 'Thành viên không còn thuộc Server này.');

  // Custom 400 message
  const errCustom = {
    response: {
      status: 400,
      data: {
        message: 'User ID không hợp lệ',
      },
    },
  };
  assert.equal(getRemoveMemberErrorMessage(errCustom, memberName), 'User ID không hợp lệ');
});

test('getRemoveMemberErrorMessage handles network, timeout, and unknown errors safely', () => {
  const memberName = 'David';

  const timeoutErr = { code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded' };
  assert.equal(getRemoveMemberErrorMessage(timeoutErr, memberName), 'Yêu cầu quá thời gian chờ. Vui lòng kiểm tra lại kết nối mạng!');

  const networkErr = { code: 'ERR_NETWORK', message: 'Network Error' };
  assert.equal(getRemoveMemberErrorMessage(networkErr, memberName), 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!');

  const unknownErr = { response: { status: 500, data: {} } };
  assert.equal(getRemoveMemberErrorMessage(unknownErr, memberName), 'Không thể loại David. Vui lòng thử lại sau!');
});

test('serverApi.removeMember sends DELETE request to /v1/servers/{serverId}/members/{userId}', async () => {
  let capturedUrl = null;
  const mockAxiosClient = {
    delete: (url) => {
      capturedUrl = url;
      return Promise.resolve({ status: 204, data: null });
    },
  };

  const removeMember = (serverId, userId) =>
    mockAxiosClient.delete(`/v1/servers/${serverId}/members/${userId}`);

  const res = await removeMember(42, 99);
  assert.equal(capturedUrl, '/v1/servers/42/members/99');
  assert.equal(res.status, 204);
});
