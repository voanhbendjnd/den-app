import test from 'node:test';
import assert from 'node:assert/strict';
import {
  formatJoinDate,
  extractProfileData,
  getProfileErrorMessage,
  getRoleDisplays,
} from '../src/utils/profileUtils.js';

test('formatJoinDate formats valid dates correctly in both compact and verbose modes', () => {
  // ISO-8601 string
  const isoDate = '2026-10-25T15:30:00Z';
  assert.equal(formatJoinDate(isoDate, false), '25/10/2026');
  assert.equal(formatJoinDate(isoDate, true), '25 tháng 10, 2026');

  // Simple YYYY-MM-DD
  const simpleDate = '2026-05-09';
  const formattedSimple = formatJoinDate(simpleDate, false);
  assert.match(formattedSimple, /09\/05\/2026/);

  // Timestamp number
  const timestamp = new Date(2026, 9, 25).getTime(); // Oct 25, 2026
  assert.equal(formatJoinDate(timestamp, false), '25/10/2026');
  assert.equal(formatJoinDate(timestamp, true), '25 tháng 10, 2026');
});

test('formatJoinDate handles edge cases and invalid date inputs gracefully', () => {
  assert.equal(formatJoinDate(null), 'Chưa xác định');
  assert.equal(formatJoinDate(undefined), 'Chưa xác định');
  assert.equal(formatJoinDate(''), 'Chưa xác định');
  assert.equal(formatJoinDate('invalid-date-string'), 'Chưa xác định');
  assert.equal(formatJoinDate({}), 'Chưa xác định');
});

test('extractProfileData unpacks nested backend ResFormatResponse data correctly', () => {
  const backendResponse = {
    data: {
      statusCode: 200,
      message: 'Lấy thông tin hồ sơ cá nhân thành công',
      data: {
        id: 10,
        email: 'kyanh@denhub.vn',
        name: 'Kỳ Anh',
        username: 'kyanh',
        avatar: 'https://cdn.denhub.vn/avatars/kyanh.png',
        activated: true,
        createdDate: '2026-10-25T15:00:00Z',
        authorities: ['ROLE_USER', 'ROLE_ADMIN'],
      },
    },
  };

  const profile = extractProfileData(backendResponse);
  assert.ok(profile);
  assert.equal(profile.id, 10);
  assert.equal(profile.email, 'kyanh@denhub.vn');
  assert.equal(profile.name, 'Kỳ Anh');
  assert.equal(profile.username, 'kyanh');
  assert.equal(profile.avatar, 'https://cdn.denhub.vn/avatars/kyanh.png');
  assert.equal(profile.activated, true);
  assert.equal(profile.createdDate, '2026-10-25T15:00:00Z');
  assert.deepEqual(profile.authorities, ['ROLE_USER', 'ROLE_ADMIN']);
});

test('extractProfileData handles direct payload and fallbacks correctly', () => {
  // Without username or authorities array
  const rawPayload = {
    id: 42,
    email: 'developer@example.com',
    authorities: 'ROLE_ADMIN',
  };

  const profile = extractProfileData(rawPayload);
  assert.ok(profile);
  assert.equal(profile.id, 42);
  assert.equal(profile.email, 'developer@example.com');
  assert.equal(profile.username, 'developer'); // Fallback from email prefix
  assert.equal(profile.name, 'developer'); // Fallback from username
  assert.equal(profile.avatar, null);
  assert.equal(profile.activated, true);
  assert.deepEqual(profile.authorities, ['ROLE_ADMIN']);
});

test('extractProfileData returns null for invalid inputs', () => {
  assert.equal(extractProfileData(null), null);
  assert.equal(extractProfileData(undefined), null);
  assert.equal(extractProfileData('string-not-object'), null);
  assert.equal(extractProfileData(12345), null);
});

test('getProfileErrorMessage maps standard HTTP error codes to clear Vietnamese text', () => {
  // 401 Unauthorized
  assert.equal(
    getProfileErrorMessage({ response: { status: 401 } }),
    'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  );

  // 403 Forbidden
  assert.equal(
    getProfileErrorMessage({ response: { status: 403 } }),
    'Bạn không có quyền truy cập hồ sơ này.'
  );

  // 404 Not Found
  assert.equal(
    getProfileErrorMessage({ response: { status: 404 } }),
    'Không tìm thấy thông tin tài khoản người dùng.'
  );

  // 500 Server Error with custom message
  assert.equal(
    getProfileErrorMessage({
      response: {
        status: 500,
        data: { message: 'Cơ sở dữ liệu người dùng đang bận.' },
      },
    }),
    'Cơ sở dữ liệu người dùng đang bận.'
  );

  // Network Error
  assert.equal(
    getProfileErrorMessage({ code: 'ERR_NETWORK' }),
    'Không thể kết nối đến máy chủ. Vui lòng kiểm tra đường truyền mạng.'
  );

  // Fallback
  assert.equal(
    getProfileErrorMessage({ message: 'Custom unknown failure' }),
    'Custom unknown failure'
  );
  assert.equal(getProfileErrorMessage(null), 'Không thể tải thông tin hồ sơ.');
});

test('getRoleDisplays maps authorities to rich UI badge representations', () => {
  const displays = getRoleDisplays(['ROLE_ADMIN', 'ROLE_USER']);
  assert.equal(displays.length, 2);

  const adminRole = displays.find((r) => r.authority === 'ROLE_ADMIN');
  assert.ok(adminRole);
  assert.equal(adminRole.label, 'Quản trị viên');
  assert.equal(adminRole.tagColor, 'red');

  const userRole = displays.find((r) => r.authority === 'ROLE_USER');
  assert.ok(userRole);
  assert.equal(userRole.label, 'Thành viên');
  assert.equal(userRole.tagColor, 'cyan');

  // Fallback for empty authorities
  const fallbackDisplays = getRoleDisplays([]);
  assert.equal(fallbackDisplays.length, 1);
  assert.equal(fallbackDisplays[0].label, 'Thành viên');
  assert.equal(fallbackDisplays[0].tagColor, 'cyan');
});
