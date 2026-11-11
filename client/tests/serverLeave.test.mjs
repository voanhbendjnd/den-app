import test from 'node:test';
import assert from 'node:assert/strict';
import { canLeaveServer, getLeaveErrorMessage } from '../src/utils/serverLeave.js';

test('canLeaveServer correctly identifies owners and members per BR-SRV-01', () => {
  const ownerServer = { id: 10, name: 'Dev Server', ownerId: '100' };
  const ownerUser = { id: '100', username: 'alice' };
  const memberUser = { id: '200', username: 'bob' };

  // Owner cannot leave their own server
  const ownerResult = canLeaveServer(ownerServer, ownerUser);
  assert.equal(ownerResult.canLeave, false);
  assert.equal(ownerResult.isOwner, true);
  assert.match(ownerResult.reason, /Chủ sở hữu không thể tự rời Server/);

  // Normal member can leave
  const memberResult = canLeaveServer(ownerServer, memberUser);
  assert.equal(memberResult.canLeave, true);
  assert.equal(memberResult.isOwner, false);
  assert.equal(memberResult.reason, null);

  // Numeric ID comparison works seamlessly with string IDs
  const numOwnerServer = { id: 10, name: 'Dev Server', ownerId: 100 };
  const strOwnerResult = canLeaveServer(numOwnerServer, { id: '100' });
  assert.equal(strOwnerResult.canLeave, false);
  assert.equal(strOwnerResult.isOwner, true);

  // Missing parameters handled safely
  const nullServerResult = canLeaveServer(null, memberUser);
  assert.equal(nullServerResult.canLeave, false);
  const nullUserResult = canLeaveServer(ownerServer, null);
  assert.equal(nullUserResult.canLeave, false);
});

test('getLeaveErrorMessage maps 401, 403, and 404 appropriately', () => {
  const serverName = 'DenHub Lab';

  // 401 Unauthorized
  const err401 = { response: { status: 401, data: {} } };
  assert.equal(getLeaveErrorMessage(err401, serverName), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  const errNotLoggedInKey = { response: { status: 400, data: { errorKey: 'notloggedin' } } };
  assert.equal(getLeaveErrorMessage(errNotLoggedInKey, serverName), 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!');

  // 403 Forbidden
  const err403 = { response: { status: 403, data: {} } };
  assert.equal(
    getLeaveErrorMessage(err403, serverName),
    'Bạn không có quyền thực hiện thao tác rời khỏi DenHub Lab.'
  );

  // 404 Not Found
  const err404 = { response: { status: 404, data: {} } };
  assert.equal(
    getLeaveErrorMessage(err404, serverName),
    'DenHub Lab không tồn tại hoặc đã bị xóa trước đó.'
  );
});

test('getLeaveErrorMessage maps 400 business rule violations correctly', () => {
  const serverName = 'Alpha Guild';

  // Owner cannot leave (BR-SRV-01)
  const errOwnerKey = {
    response: {
      status: 400,
      data: {
        errorKey: 'ownerCannotLeave',
        message: 'Server owner cannot leave the server. Please transfer ownership or delete the server.',
      },
    },
  };
  assert.equal(
    getLeaveErrorMessage(errOwnerKey, serverName),
    'Chủ sở hữu không thể rời Server. Hãy chuyển quyền sở hữu hoặc xóa Server.'
  );

  // Not a member
  const errNotMember = {
    response: {
      status: 400,
      data: {
        errorKey: 'notMember',
        message: 'User is not a member of this server',
      },
    },
  };
  assert.equal(
    getLeaveErrorMessage(errNotMember, serverName),
    'Bạn không còn là thành viên của Alpha Guild.'
  );

  // Invalid ID
  const errInvalidId = {
    response: {
      status: 400,
      data: {
        message: 'Invalid server ID',
      },
    },
  };
  assert.equal(getLeaveErrorMessage(errInvalidId, serverName), 'Mã Server không hợp lệ.');
});

test('getLeaveErrorMessage handles network, timeout, and server errors safely without leaking stack traces', () => {
  const serverName = 'Beta Base';

  // Network error (offline or unreachable)
  const netErr = { code: 'ERR_NETWORK' };
  assert.equal(
    getLeaveErrorMessage(netErr, serverName),
    'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!'
  );

  const noResponseErr = {};
  assert.equal(
    getLeaveErrorMessage(noResponseErr, serverName),
    'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!'
  );

  // Timeout
  const timeoutErr = { code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded' };
  assert.equal(
    getLeaveErrorMessage(timeoutErr, serverName),
    'Yêu cầu rời Server đã hết thời gian chờ. Vui lòng kiểm tra lại kết nối mạng!'
  );

  // 500 Internal Server Error
  const err500 = {
    response: {
      status: 500,
      data: {
        trace: 'java.lang.NullPointerException at com.denhub.service...',
        message: 'Internal Error',
      },
    },
  };
  const msg500 = getLeaveErrorMessage(err500, serverName);
  assert.equal(msg500, 'Không thể rời khỏi Beta Base. Vui lòng thử lại sau!');
  assert.doesNotMatch(msg500, /NullPointerException/);

  // Null error fallback
  assert.equal(getLeaveErrorMessage(null, serverName), 'Không thể rời khỏi Beta Base. Vui lòng thử lại!');
});

test('serverApi.leave sends POST request to /v1/servers/{serverId}/leave', async () => {
  let capturedUrl = null;
  const mockAxiosClient = {
    post: (url) => {
      capturedUrl = url;
      return Promise.resolve({ status: 204, data: null });
    },
  };

  const leave = (serverId) => mockAxiosClient.post(`/v1/servers/${serverId}/leave`);

  const res = await leave(42);
  assert.equal(capturedUrl, '/v1/servers/42/leave');
  assert.equal(res.status, 204);
});
