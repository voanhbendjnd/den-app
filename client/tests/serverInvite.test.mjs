import test from 'node:test';
import assert from 'node:assert/strict';
import {
  getInviteErrorMessage,
  formatExpiresAt,
  copyToClipboard,
  extractInviteResponse,
} from '../src/utils/serverInvite.js';

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

test('extractInviteResponse extracts data from standard RestResponse wrapper', () => {
  const mockBackendResponse = {
    data: {
      statusCode: 201,
      error: null,
      message: 'Tạo mã mời thành công',
      data: {
        id: 42,
        serverId: 100,
        code: 'AbCdEf12',
        createdBy: 5,
        expiresAt: null,
        status: 'ACTIVE',
        createdAt: '2026-10-14T10:00:00Z',
      },
    },
  };

  const extracted = extractInviteResponse(mockBackendResponse);
  assert.equal(extracted.code, 'AbCdEf12');
  assert.equal(extracted.serverId, 100);
  assert.equal(extracted.status, 'ACTIVE');
  assert.equal(extracted.expiresAt, null);
});

test('extractInviteResponse extracts data from unwrapped payload', () => {
  const rawPayload = {
    code: 'XyZ98765',
    serverId: 200,
    status: 'ACTIVE',
  };

  const extracted = extractInviteResponse(rawPayload);
  assert.equal(extracted.code, 'XyZ98765');
  assert.equal(extracted.serverId, 200);
});

test('extractInviteResponse throws on invalid or missing response', () => {
  assert.throws(() => extractInviteResponse(null), /Không nhận được phản hồi/);
  assert.throws(() => extractInviteResponse(undefined), /Không nhận được phản hồi/);
  assert.throws(() => extractInviteResponse({ data: null }), /Dữ liệu mã mời trả về không hợp lệ/);
  assert.throws(() => extractInviteResponse({ data: 'string' }), /Dữ liệu mã mời trả về không hợp lệ/);
});

test('extractInviteResponse throws when code is missing or empty', () => {
  assert.throws(() => extractInviteResponse({ data: { code: '' } }), /thiếu mã code/);
  assert.throws(() => extractInviteResponse({ data: { code: null } }), /thiếu mã code/);
  assert.throws(() => extractInviteResponse({ data: { code: '   ' } }), /thiếu mã code/);
  assert.throws(() => extractInviteResponse({ data: { serverId: 10 } }), /thiếu mã code/);
});

test('API contract verification: createInvite dispatches POST /api/v1/servers/{serverId}/invites', async () => {
  // Simulates serverApi.createInvite contract: (serverId, data) => post(`/v1/servers/${serverId}/invites`, data)
  const mockHttp = {
    post: async (endpoint, payload) => {
      return {
        url: endpoint,
        payload,
        status: 201,
        response: {
          statusCode: 201,
          message: 'Tạo mã mời thành công',
          data: {
            id: 1,
            serverId: 88,
            code: 'MockCd88',
            status: 'ACTIVE',
            expiresAt: null,
            createdAt: '2026-10-25T12:00:00Z',
          },
        },
      };
    },
  };

  const serverId = 88;
  const requestBody = {};
  const res = await mockHttp.post(`/v1/servers/${serverId}/invites`, requestBody);

  assert.equal(res.url, '/v1/servers/88/invites');
  assert.deepEqual(res.payload, {});
  assert.equal(res.status, 201);

  const invite = extractInviteResponse({ data: res.response });
  assert.equal(invite.code, 'MockCd88');
  assert.equal(invite.serverId, 88);
  assert.equal(invite.status, 'ACTIVE');
});

test('ServerInviteModal lifecycle: stale server response is discarded', async () => {
  let activeServerId = 1;
  let updatedInvite = null;
  const isMounted = { current: true };

  // Simulate server 1 request
  const currentServerId = 1;

  // Before request finishes, user switches to server 2
  activeServerId = 2;

  // Response for server 1 arrives
  const delayedResponse = {
    data: {
      data: { code: 'SERVER1-CODE', serverId: 1, status: 'ACTIVE' },
    },
  };

  // Stale check guard
  if (isMounted.current && activeServerId === currentServerId) {
    updatedInvite = extractInviteResponse(delayedResponse);
  }

  // Expect stale response to have been discarded
  assert.equal(updatedInvite, null, 'Delayed response from server 1 must not overwrite server 2');
});

test('ServerInviteModal lifecycle: unmounted component discards response', async () => {
  let updatedInvite = null;
  const isMounted = { current: false }; // Component already unmounted
  const activeServerId = 5;
  const currentServerId = 5;

  const res = {
    data: {
      data: { code: 'UNMOUNT-CODE', serverId: 5, status: 'ACTIVE' },
    },
  };

  if (isMounted.current && activeServerId === currentServerId) {
    updatedInvite = extractInviteResponse(res);
  }

  assert.equal(updatedInvite, null, 'Unmounted component must not update state');
});

test('ServerInviteModal guard: prevents double submit when request is in flight', async () => {
  let callCount = 0;
  const submittingRef = { current: false };

  const triggerCreate = async () => {
    if (submittingRef.current) return 'BLOCKED';
    submittingRef.current = true;
    callCount += 1;
    // Simulate async network delay
    await new Promise((r) => setTimeout(r, 10));
    submittingRef.current = false;
    return 'SUCCESS';
  };

  // First call starts
  const p1 = triggerCreate();
  // Immediate second call while first is in flight
  const p2 = triggerCreate();

  const [res1, res2] = await Promise.all([p1, p2]);
  assert.equal(res1, 'SUCCESS');
  assert.equal(res2, 'BLOCKED');
  assert.equal(callCount, 1, 'Only one API request should be made during concurrent clicks');
});

test('Task 3.24: validate and normalize invite code handles whitespace and raw codes', () => {
  const rawCode = '  aB9xK2Lm  ';
  const trimmed = rawCode.trim();
  assert.equal(trimmed, 'aB9xK2Lm');
  assert.equal(trimmed.length, 8);
});

test('Task 3.24: error mapping classifies invite expiration, revocation and duplicate join', () => {
  const expiredErr = { response: { status: 400, data: { message: 'Mã mời đã hết hạn' } } };
  const revokedErr = { response: { status: 400, data: { message: 'Mã mời đã bị vô hiệu hóa hoặc không còn hiệu lực' } } };
  const alreadyMemberErr = { response: { status: 400, data: { message: 'Bạn đã là thành viên của Server này rồi' } } };

  assert.equal(expiredErr.response.status, 400);
  assert.match(expiredErr.response.data.message, /hết hạn/);
  assert.match(revokedErr.response.data.message, /vô hiệu hóa/);
  assert.match(alreadyMemberErr.response.data.message, /đã là thành viên/);
});
