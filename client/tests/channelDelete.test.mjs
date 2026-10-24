import test from 'node:test';
import assert from 'node:assert/strict';
import { 
  canDeleteChannel, 
  isChannelDeletable,
  getChannelDeleteErrorMessage 
} from '../src/utils/channelUtils.js';

test('canDeleteChannel returns detailed verification object', () => {
  // Invalid channels
  assert.deepEqual(canDeleteChannel(null), { canDelete: false, reason: 'Thông tin kênh không hợp lệ!' });
  assert.deepEqual(canDeleteChannel(undefined), { canDelete: false, reason: 'Thông tin kênh không hợp lệ!' });
  assert.deepEqual(canDeleteChannel({}), { canDelete: false, reason: 'Thông tin kênh không hợp lệ!' });
  assert.deepEqual(canDeleteChannel({ name: 'random' }), { canDelete: false, reason: 'Thông tin kênh không hợp lệ!' });

  // General channel variations (case-insensitive)
  assert.deepEqual(canDeleteChannel({ id: 1, name: 'general' }, 5), {
    canDelete: false,
    reason: 'Kênh #general là kênh mặc định và không thể bị xóa!',
  });
  assert.deepEqual(canDeleteChannel({ id: 1, name: ' General ' }, 5), {
    canDelete: false,
    reason: 'Kênh #general là kênh mặc định và không thể bị xóa!',
  });
  assert.deepEqual(canDeleteChannel({ id: 1, name: 'GENERAL' }, 5), {
    canDelete: false,
    reason: 'Kênh #general là kênh mặc định và không thể bị xóa!',
  });

  // Last channel in server
  assert.deepEqual(canDeleteChannel({ id: 2, name: 'random' }, 1), {
    canDelete: false,
    reason: 'Không thể xóa kênh duy nhất trong Server!',
  });
  assert.deepEqual(canDeleteChannel({ id: 2, name: 'random' }, 0), {
    canDelete: false,
    reason: 'Không thể xóa kênh duy nhất trong Server!',
  });

  // Valid deletable channel
  assert.deepEqual(canDeleteChannel({ id: 2, name: 'random' }, 2), {
    canDelete: true,
    reason: null,
  });
  assert.deepEqual(canDeleteChannel({ id: 3, name: 'dev-chat' }, 5), {
    canDelete: true,
    reason: null,
  });
  assert.deepEqual(canDeleteChannel({ id: 4, name: 'announcements' }), {
    canDelete: true,
    reason: null,
  });
});

test('isChannelDeletable returns boolean flag correctly', () => {
  assert.equal(isChannelDeletable(null), false);
  assert.equal(isChannelDeletable({ id: 1, name: 'general' }, 5), false);
  assert.equal(isChannelDeletable({ id: 2, name: 'dev-chat' }, 1), false);
  assert.equal(isChannelDeletable({ id: 2, name: 'dev-chat' }, 2), true);
  assert.equal(isChannelDeletable({ id: 3, name: 'announcements' }), true);
});

test('getChannelDeleteErrorMessage maps general channel protection error correctly', () => {
  const errKey = {
    response: {
      data: {
        errorKey: 'cannotdeletegeneral',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errKey, 'general'),
    'Kênh #general là kênh mặc định và không thể bị xóa!'
  );

  const errMessage = {
    response: {
      data: {
        message: 'Cannot delete general channel',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errMessage),
    'Kênh #general là kênh mặc định và không thể bị xóa!'
  );
});

test('getChannelDeleteErrorMessage maps last channel protection error correctly', () => {
  const errKey = {
    response: {
      data: {
        errorKey: 'cannotdeletelastchannel',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errKey),
    'Không thể xóa kênh cuối cùng trong Server!'
  );

  const errMessage = {
    response: {
      data: {
        message: 'Không thể xóa kênh cuối cùng trong server',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errMessage),
    'Không thể xóa kênh cuối cùng trong Server!'
  );
});

test('getChannelDeleteErrorMessage maps permission and not found errors', () => {
  const errForbiddenKey = {
    response: {
      data: {
        errorKey: 'forbidden',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errForbiddenKey),
    'Bạn không có quyền xóa kênh trong server này!'
  );

  const errForbiddenStatus = {
    response: {
      status: 403,
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errForbiddenStatus),
    'Bạn không có quyền xóa kênh trong server này!'
  );

  const errNotFoundKey = {
    response: {
      data: {
        errorKey: 'channelnotfound',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errNotFoundKey),
    'Kênh không tồn tại hoặc đã bị xóa trước đó!'
  );

  const errNotFoundStatus = {
    response: {
      status: 404,
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errNotFoundStatus),
    'Kênh không tồn tại hoặc đã bị xóa trước đó!'
  );
});

test('getChannelDeleteErrorMessage maps generic backend and fallback errors', () => {
  const errConflict = {
    response: {
      data: {
        errorKey: 'conflict',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errConflict),
    'Không thể xóa kênh an toàn vì dữ liệu tin nhắn đang liên kết với hội thoại riêng!'
  );

  const errInvalidId = {
    response: {
      data: {
        errorKey: 'invalidid',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errInvalidId),
    'Mã kênh không hợp lệ!'
  );

  const errCustom = {
    response: {
      data: {
        message: 'Lỗi cơ sở dữ liệu tạm thời',
      },
    },
  };
  assert.equal(
    getChannelDeleteErrorMessage(errCustom),
    'Lỗi cơ sở dữ liệu tạm thời'
  );

  const errUnknown = {};
  assert.equal(
    getChannelDeleteErrorMessage(errUnknown, 'test-channel'),
    'Không thể xóa kênh "#test-channel". Vui lòng thử lại sau!'
  );

  assert.equal(
    getChannelDeleteErrorMessage(errUnknown),
    'Không thể xóa kênh. Vui lòng thử lại sau!'
  );
});
