import test from 'node:test';
import assert from 'node:assert/strict';
import {
  truncatePinnedContent,
  formatPinnedSummary,
  formatPinnedTitle,
  canUnpinMessage,
} from '../src/utils/pinnedMessages.js';

test('truncatePinnedContent collapses whitespace and limits character length', () => {
  assert.equal(truncatePinnedContent(null), '');
  assert.equal(truncatePinnedContent(''), '');
  assert.equal(truncatePinnedContent('  Xin chào   mọi người!  '), 'Xin chào mọi người!');
  assert.equal(truncatePinnedContent('Ngắn', 10), 'Ngắn');
  assert.equal(
    truncatePinnedContent('Đây là một tin nhắn rất dài cần phải được rút gọn khi hiển thị ở trạng thái thu gọn', 25),
    'Đây là một tin nhắn rất d...'
  );
});

test('formatPinnedSummary combines author name and content cleanly', () => {
  assert.equal(formatPinnedSummary('Hồ Minh Duy', 'QA_TEST_chat_20261111_2305'), 'Hồ Minh Duy: QA_TEST_chat_20261111_2305');
  assert.equal(formatPinnedSummary('', 'Nội dung'), 'Thành viên: Nội dung');
  assert.equal(formatPinnedSummary('Duy', ''), 'Duy');
  assert.equal(
    formatPinnedSummary('Admin', 'Một tin nhắn dài hơn bình thường để kiểm tra việc rút gọn', 20),
    'Admin: Một tin nhắn dài hơn...'
  );
});

test('formatPinnedTitle formats title as Author · Tin nhắn đã ghim', () => {
  assert.equal(formatPinnedTitle('Thành viên #6'), 'Thành viên #6 · Tin nhắn đã ghim');
  assert.equal(formatPinnedTitle(''), 'Thành viên · Tin nhắn đã ghim');
  assert.equal(formatPinnedTitle('  Duy  '), 'Duy · Tin nhắn đã ghim');
});

test('canUnpinMessage restricts unpinning to server owner', () => {
  assert.equal(canUnpinMessage(true), true);
  assert.equal(canUnpinMessage(false), false);
  assert.equal(canUnpinMessage(null), false);
  assert.equal(canUnpinMessage(undefined), false);
});

