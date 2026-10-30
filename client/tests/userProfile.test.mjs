import test from 'node:test';
import assert from 'node:assert/strict';
import {
  formatJoinDate,
  extractProfileData,
  getProfileErrorMessage,
  getRoleDisplays,
  validateProfileUpdate,
  sanitizeProfilePayload,
  getProfileUpdateErrorMessage,
  validatePasswordChange,
  getPasswordChangeErrorMessage,
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

test('validateProfileUpdate validates valid inputs without any errors', () => {
  const validData = {
    name: 'Kỳ Anh Đỗ',
    username: 'kyanh_ce',
    avatar: 'https://cdn.denhub.vn/avatars/kyanh.png',
  };

  const result = validateProfileUpdate(validData);
  assert.equal(result.isValid, true);
  assert.deepEqual(result.errors, {});
});

test('validateProfileUpdate flags name errors when exceeding length or whitespace only', () => {
  // Name too long (> 50 chars)
  const longName = 'A'.repeat(51);
  const resultLong = validateProfileUpdate({ name: longName });
  assert.equal(resultLong.isValid, false);
  assert.match(resultLong.errors.name, /vượt quá 50 ký tự/);

  // Whitespace only
  const whitespaceName = '     ';
  const resultWs = validateProfileUpdate({ name: whitespaceName });
  assert.equal(resultWs.isValid, false);
  assert.match(resultWs.errors.name, /chỉ chứa khoảng trắng/);
});

test('validateProfileUpdate flags username errors for length, pattern, and empty values', () => {
  // Empty username
  const emptyRes = validateProfileUpdate({ username: '' });
  assert.equal(emptyRes.isValid, false);
  assert.match(emptyRes.errors.username, /không được để trống/);

  // Too short (< 3 chars)
  const shortRes = validateProfileUpdate({ username: 'ab' });
  assert.equal(shortRes.isValid, false);
  assert.match(shortRes.errors.username, /từ 3 đến 50 ký tự/);

  // Too long (> 50 chars)
  const longRes = validateProfileUpdate({ username: 'u'.repeat(51) });
  assert.equal(longRes.isValid, false);
  assert.match(longRes.errors.username, /từ 3 đến 50 ký tự/);

  // Invalid special characters (spaces, @, #, etc.)
  const specialChars = ['user name', 'user@denhub', 'user#123', 'user!name', 'user$'];
  for (const invalidUsername of specialChars) {
    const res = validateProfileUpdate({ username: invalidUsername });
    assert.equal(res.isValid, false, `Expected ${invalidUsername} to be invalid`);
    assert.match(res.errors.username, /chỉ được chứa chữ cái, chữ số/);
  }

  // Valid characters (letters, numbers, _, -, .)
  const validUsernames = ['kyanh_123', 'john-doe', 'alex.dev', 'User_99', 'pro.gamer-01'];
  for (const validU of validUsernames) {
    const res = validateProfileUpdate({ username: validU });
    assert.equal(res.isValid, true, `Expected ${validU} to be valid`);
  }
});

test('validateProfileUpdate flags avatar errors when exceeding length or invalid protocol', () => {
  // Length > 500
  const longUrl = 'https://denhub.vn/' + 'x'.repeat(500);
  const longRes = validateProfileUpdate({ avatar: longUrl });
  assert.equal(longRes.isValid, false);
  assert.match(longRes.errors.avatar, /vượt quá 500 ký tự/);

  // Invalid protocol
  const invalidUrl = 'ftp://invalid-avatar.png';
  const protoRes = validateProfileUpdate({ avatar: invalidUrl });
  assert.equal(protoRes.isValid, false);
  assert.match(protoRes.errors.avatar, /http:\/\/ hoặc https:\/\//);

  // Empty avatar is valid (clearing avatar)
  const emptyRes = validateProfileUpdate({ avatar: '' });
  assert.equal(emptyRes.isValid, true);
});

test('sanitizeProfilePayload trims fields, lowercases username, and converts empty avatar to null', () => {
  const raw = {
    name: '  Kỳ Anh  ',
    username: '  KyAnh_Dev  ',
    avatar: '   ',
  };

  const sanitized = sanitizeProfilePayload(raw);
  assert.equal(sanitized.name, 'Kỳ Anh');
  assert.equal(sanitized.username, 'kyanh_dev');
  assert.equal(sanitized.avatar, null);

  // Preserves valid avatar URL
  const withAvatar = sanitizeProfilePayload({ avatar: '  https://avatar.png  ' });
  assert.equal(withAvatar.avatar, 'https://avatar.png');
});

test('getProfileUpdateErrorMessage correctly maps business errors, field errors, and network issues', () => {
  // Username already exists
  const usernameTakenError = {
    response: {
      status: 400,
      data: {
        errorKey: 'usernamealreadyexists',
        message: 'Username already in use',
      },
    },
  };
  const msgTaken = getProfileUpdateErrorMessage(usernameTakenError, 'kyanh_taken');
  assert.match(msgTaken, /@kyanh_taken đã có người sử dụng/);

  // Not logged in (401 or notloggedin key)
  const notLoggedInError = {
    response: {
      status: 401,
      data: { errorKey: 'notloggedin' },
    },
  };
  assert.match(getProfileUpdateErrorMessage(notLoggedInError), /Phiên đăng nhập đã hết hạn/);

  // User not found (404 or usernotfound key)
  const userNotFoundError = {
    response: {
      status: 404,
      data: { errorKey: 'usernotfound' },
    },
  };
  assert.match(getProfileUpdateErrorMessage(userNotFoundError), /Không tìm thấy thông tin tài khoản/);

  // Bean validation field errors
  const fieldError = {
    response: {
      status: 400,
      data: {
        fieldErrors: [
          { field: 'username', message: 'Username must be 3-50 characters' },
        ],
      },
    },
  };
  const msgField = getProfileUpdateErrorMessage(fieldError);
  assert.match(msgField, /Tên người dùng: Username must be 3-50 characters/);

  // Network error
  assert.match(
    getProfileUpdateErrorMessage({ code: 'ERR_NETWORK' }),
    /Không thể kết nối đến máy chủ/
  );

  // Null error fallback
  assert.equal(getProfileUpdateErrorMessage(null), 'Cập nhật hồ sơ thất bại.');
});

test('validatePasswordChange validates correct matching password input', () => {
  const validData = {
    currentPassword: 'OldPassword123',
    newPassword: 'NewPassword456',
    confirmPassword: 'NewPassword456',
  };

  const result = validatePasswordChange(validData);
  assert.equal(result.isValid, true);
  assert.deepEqual(result.errors, {});
});

test('validatePasswordChange flags errors when currentPassword is missing or whitespace only', () => {
  const resultEmpty = validatePasswordChange({
    currentPassword: '',
    newPassword: 'NewPassword456',
    confirmPassword: 'NewPassword456',
  });
  assert.equal(resultEmpty.isValid, false);
  assert.match(resultEmpty.errors.currentPassword, /nhập mật khẩu hiện tại/);

  const resultWs = validatePasswordChange({
    currentPassword: '   ',
    newPassword: 'NewPassword456',
    confirmPassword: 'NewPassword456',
  });
  assert.equal(resultWs.isValid, false);
  assert.match(resultWs.errors.currentPassword, /nhập mật khẩu hiện tại/);
});

test('validatePasswordChange flags errors when newPassword is too short, too long, or same as currentPassword', () => {
  // Too short (< 4 chars)
  const resultShort = validatePasswordChange({
    currentPassword: 'OldPassword123',
    newPassword: '123',
    confirmPassword: '123',
  });
  assert.equal(resultShort.isValid, false);
  assert.match(resultShort.errors.newPassword, /từ 4 đến 100 ký tự/);

  // Too long (> 100 chars)
  const resultLong = validatePasswordChange({
    currentPassword: 'OldPassword123',
    newPassword: 'a'.repeat(101),
    confirmPassword: 'a'.repeat(101),
  });
  assert.equal(resultLong.isValid, false);
  assert.match(resultLong.errors.newPassword, /từ 4 đến 100 ký tự/);

  // Same as current password
  const resultSame = validatePasswordChange({
    currentPassword: 'SameSecret123',
    newPassword: 'SameSecret123',
    confirmPassword: 'SameSecret123',
  });
  assert.equal(resultSame.isValid, false);
  assert.match(resultSame.errors.newPassword, /không được trùng với mật khẩu hiện tại/);
});

test('validatePasswordChange flags errors when confirmPassword does not match newPassword', () => {
  // Empty confirm password
  const resultEmptyConfirm = validatePasswordChange({
    currentPassword: 'OldPassword123',
    newPassword: 'NewPassword456',
    confirmPassword: '',
  });
  assert.equal(resultEmptyConfirm.isValid, false);
  assert.match(resultEmptyConfirm.errors.confirmPassword, /xác nhận lại mật khẩu mới/);

  // Mismatched confirm password
  const resultMismatch = validatePasswordChange({
    currentPassword: 'OldPassword123',
    newPassword: 'NewPassword456',
    confirmPassword: 'DifferentPassword789',
  });
  assert.equal(resultMismatch.isValid, false);
  assert.match(resultMismatch.errors.confirmPassword, /không khớp/);
});

test('getPasswordChangeErrorMessage maps business error keys and validation details', () => {
  // Incorrect current password
  const wrongPassErr = {
    response: {
      status: 400,
      data: { errorKey: 'invalidcurrentpassword' },
    },
  };
  assert.match(getPasswordChangeErrorMessage(wrongPassErr), /Mật khẩu hiện tại không chính xác/);

  // Same password
  const samePassErr = {
    response: {
      status: 400,
      data: { errorKey: 'samepassword' },
    },
  };
  assert.match(getPasswordChangeErrorMessage(samePassErr), /không được trùng với mật khẩu hiện tại/);

  // Not logged in
  const notLoggedInErr = {
    response: {
      status: 401,
      data: { errorKey: 'notloggedin' },
    },
  };
  assert.match(getPasswordChangeErrorMessage(notLoggedInErr), /Phiên đăng nhập đã hết hạn/);

  // User not found
  const notFoundErr = {
    response: {
      status: 404,
      data: { errorKey: 'usernotfound' },
    },
  };
  assert.match(getPasswordChangeErrorMessage(notFoundErr), /Không tìm thấy thông tin tài khoản/);

  // Bean validation field error
  const fieldErr = {
    response: {
      status: 400,
      data: {
        fieldErrors: [
          { field: 'newPassword', message: 'Mật khẩu mới phải từ 4 đến 100 ký tự' },
        ],
      },
    },
  };
  assert.match(getPasswordChangeErrorMessage(fieldErr), /Mật khẩu mới: Mật khẩu mới phải từ 4 đến 100 ký tự/);

  // Network error
  assert.match(
    getPasswordChangeErrorMessage({ code: 'ERR_NETWORK' }),
    /Không thể kết nối đến máy chủ/
  );

  // Fallback
  assert.equal(getPasswordChangeErrorMessage(null), 'Đổi mật khẩu thất bại.');
});

