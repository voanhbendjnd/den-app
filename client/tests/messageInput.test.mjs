import test from 'node:test';
import assert from 'node:assert/strict';
import { getMessageSendError, getMessageValidationError } from '../src/utils/messageInput.js';

test('rejects empty and Unicode whitespace-only messages, including Java Unicode NEL', () => {
  for (const content of [undefined, null, '', ' \t\r\n', '\u0085', '\u2003\u00a0\u3000']) {
    assert.ok(getMessageValidationError(content), `Expected invalid: ${JSON.stringify(content)}`);
  }
});

test('accepts text with leading spaces and newlines without changing the content', () => {
  const content = '  Xin chào\nnhóm mình!  ';
  assert.equal(getMessageValidationError(content), null);
  assert.equal(content, '  Xin chào\nnhóm mình!  ');
});

test('enforces the Java/JavaScript UTF-16 boundary for ASCII and emoji', () => {
  assert.equal(getMessageValidationError('a'.repeat(4000)), null);
  assert.ok(getMessageValidationError('a'.repeat(4001)));
  assert.equal(getMessageValidationError('🙂'.repeat(2000)), null);
  assert.ok(getMessageValidationError('🙂'.repeat(2000) + 'a'));
});

test('maps authentication, permission, validation and missing-channel failures to safe messages', () => {
  const cases = [[400, 'Nội dung'], [401, 'đăng nhập'], [403, 'quyền'], [404, 'Không tìm thấy']];
  for (const [status, expected] of cases) {
    const result = getMessageSendError({ response: { status, data: { detail: 'INTERNAL_PRIVATE_DETAILS' } } });
    assert.ok(result.includes(expected));
    assert.ok(!result.includes('INTERNAL_PRIVATE_DETAILS'));
  }
});

test('network and unknown failures preserve a retryable message without exposing the error', () => {
  for (const error of [undefined, new Error('PRIVATE'), { response: { status: 500 } }]) {
    assert.match(getMessageSendError(error), /giữ lại/);
  }
});
