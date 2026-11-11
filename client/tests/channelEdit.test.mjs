import test from 'node:test';
import assert from 'node:assert/strict';
import { 
  formatChannelName, 
  validateChannelName, 
  validateChannelPosition, 
  getChannelEditErrorMessage 
} from '../src/utils/channelUtils.js';

test('formatChannelName normalizes channel names correctly', () => {
  assert.equal(formatChannelName('General'), 'general');
  assert.equal(formatChannelName('DEV CHAT'), 'dev-chat');
  assert.equal(formatChannelName('  spaced   channel  '), '-spaced-channel-');
  assert.equal(formatChannelName('kênh_thảo_luận#1'), 'knh_tho_lun1');
  assert.equal(formatChannelName('cyber-punk_2077'), 'cyber-punk_2077');
  assert.equal(formatChannelName(''), '');
  assert.equal(formatChannelName(null), '');
  assert.equal(formatChannelName(undefined), '');
});

test('validateChannelName enforces length and character rules', () => {
  assert.equal(validateChannelName(''), 'Vui lòng nhập tên kênh!');
  assert.equal(validateChannelName('   '), 'Vui lòng nhập tên kênh!');
  assert.equal(validateChannelName(null), 'Vui lòng nhập tên kênh!');
  assert.equal(validateChannelName('a'), 'Tên kênh phải có ít nhất 2 ký tự!');
  assert.equal(validateChannelName('a'.repeat(21)), 'Tên kênh không được vượt quá 20 ký tự!');
  assert.equal(validateChannelName('kênh-tiếng-việt'), 'Tên kênh chỉ được chứa chữ thường không dấu, số và dấu gạch ngang/dưới!');
  assert.equal(validateChannelName('general#1'), 'Tên kênh chỉ được chứa chữ thường không dấu, số và dấu gạch ngang/dưới!');
  assert.equal(validateChannelName('general 1'), 'Tên kênh chỉ được chứa chữ thường không dấu, số và dấu gạch ngang/dưới!');
  
  // Valid channel names
  assert.equal(validateChannelName('general'), null);
  assert.equal(validateChannelName('dev-chat'), null);
  assert.equal(validateChannelName('stage_01'), null);
  assert.equal(validateChannelName('a1'), null);
  assert.equal(validateChannelName('a'.repeat(20)), null);
});

test('validateChannelPosition validates non-negative integer or optional position', () => {
  assert.equal(validateChannelPosition(undefined), null);
  assert.equal(validateChannelPosition(null), null);
  assert.equal(validateChannelPosition(''), null);
  assert.equal(validateChannelPosition(0), null);
  assert.equal(validateChannelPosition(10), null);
  assert.equal(validateChannelPosition('5'), null);
  assert.equal(validateChannelPosition(-1), 'Thứ tự vị trí phải là số nguyên không âm (>= 0)!');
  assert.equal(validateChannelPosition(1.5), 'Thứ tự vị trí phải là số nguyên không âm (>= 0)!');
  assert.equal(validateChannelPosition('abc'), 'Thứ tự vị trí phải là số nguyên không âm (>= 0)!');
});

test('getChannelEditErrorMessage maps name already exists error correctly', () => {
  const errWithKey = {
    response: {
      data: {
        errorKey: 'namealreadyexists',
      },
    },
  };
  assert.equal(
    getChannelEditErrorMessage(errWithKey, 'announcements'),
    'Tên kênh "#announcements" đã tồn tại trong server này. Vui lòng chọn tên khác!'
  );

  const errWithMsg = {
    response: {
      data: {
        message: 'Channel with name already exists in this server',
      },
    },
  };
  assert.equal(
    getChannelEditErrorMessage(errWithMsg, 'announcements'),
    'Tên kênh "#announcements" đã tồn tại trong server này. Vui lòng chọn tên khác!'
  );
});

test('getChannelEditErrorMessage maps permission and not found errors', () => {
  const errForbidden = {
    response: {
      data: {
        errorKey: 'forbidden',
      },
    },
  };
  assert.equal(
    getChannelEditErrorMessage(errForbidden),
    'Bạn không có quyền chỉnh sửa kênh trong server này!'
  );

  const errNotFound = {
    response: {
      data: {
        errorKey: 'channelnotfound',
      },
    },
  };
  assert.equal(
    getChannelEditErrorMessage(errNotFound),
    'Kênh không tồn tại hoặc đã bị xóa!'
  );
});

test('getChannelEditErrorMessage maps validation and generic errors', () => {
  const errViolation = {
    response: {
      data: {
        violations: [{ field: 'name', message: 'Tên kênh không được để trống' }],
      },
    },
  };
  assert.equal(
    getChannelEditErrorMessage(errViolation),
    'Tên kênh không được để trống'
  );

  const errCustom = {
    response: {
      data: {
        message: 'Lỗi máy chủ nội bộ',
      },
    },
  };
  assert.equal(
    getChannelEditErrorMessage(errCustom),
    'Lỗi máy chủ nội bộ'
  );

  const errUnknown = {};
  assert.equal(
    getChannelEditErrorMessage(errUnknown),
    'Không thể cập nhật kênh. Vui lòng thử lại sau!'
  );
});
