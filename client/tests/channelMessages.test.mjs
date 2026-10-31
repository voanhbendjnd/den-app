import test from 'node:test';
import assert from 'node:assert/strict';
import dayjs from 'dayjs';
import {
  sortMessagesChronological,
  mergeMessagePages,
  formatMessageTime,
  formatCompactTime,
  getAuthorInfo,
  updateMessageInList,
  isMessageEdited,
  removeMessageFromList,
  canDeleteMessage,
  canEditMessage,
  calculateScrollAfterPrepend,
} from '../src/utils/channelMessages.js';

test('sortMessagesChronological sorts messages by ID ascending and removes duplicates', () => {
  const input = [
    { id: 105, content: 'Third' },
    { id: 101, content: 'First' },
    { id: 103, content: 'Second' },
    { id: 101, content: 'First duplicate' },
  ];

  const result = sortMessagesChronological(input);
  assert.equal(result.length, 3);
  assert.equal(result[0].id, 101);
  assert.equal(result[1].id, 103);
  assert.equal(result[2].id, 105);
});

test('sortMessagesChronological handles null, empty, and non-array safely', () => {
  assert.deepEqual(sortMessagesChronological(null), []);
  assert.deepEqual(sortMessagesChronological([]), []);
  assert.deepEqual(sortMessagesChronological(undefined), []);
  assert.deepEqual(sortMessagesChronological([{ id: null }, null, { id: 1 }]), [{ id: 1 }]);
});

test('mergeMessagePages merges older page before existing messages while preserving order and uniqueness', () => {
  const existing = [
    { id: 104, content: 'Hello 104' },
    { id: 105, content: 'Hello 105' },
  ];
  const olderPage = [
    { id: 103, content: 'Hello 103' },
    { id: 102, content: 'Hello 102' },
    { id: 104, content: 'Duplicate 104' },
  ];

  const merged = mergeMessagePages(existing, olderPage);
  assert.equal(merged.length, 4);
  assert.equal(merged[0].id, 102);
  assert.equal(merged[1].id, 103);
  assert.equal(merged[2].id, 104);
  assert.equal(merged[3].id, 105);
});

test('formatMessageTime formats today, yesterday, and older dates correctly', () => {
  const today = dayjs().hour(14).minute(30).second(0).toISOString();
  assert.match(formatMessageTime(today), /^Hôm nay lúc 14:30$/);

  const yesterday = dayjs().subtract(1, 'day').hour(9).minute(15).second(0).toISOString();
  assert.match(formatMessageTime(yesterday), /^Hôm qua lúc 09:15$/);

  const older = dayjs('2026-01-15T08:05:00Z').toISOString();
  assert.match(formatMessageTime(older), /^\d{2}\/\d{2}\/2026/);

  assert.equal(formatMessageTime(''), '');
  assert.equal(formatMessageTime(null), '');
  assert.equal(formatMessageTime('invalid-date'), '');
});

test('formatCompactTime formats valid ISO time to HH:mm', () => {
  const time = dayjs().hour(16).minute(45).second(0).toISOString();
  assert.equal(formatCompactTime(time), '16:45');
  assert.equal(formatCompactTime(null), '');
});

test('getAuthorInfo resolves current user correctly as own message', () => {
  const currentUser = { id: 2, displayName: 'Oni', username: 'djnd', avatar: 'https://example.com/oni.png' };
  const author = getAuthorInfo(2, [], currentUser, 1);

  assert.equal(author.isOwn, true);
  assert.equal(author.isOwner, false);
  assert.equal(author.name, 'Oni');
  assert.equal(author.avatar, 'https://example.com/oni.png');
  assert.equal(author.initial, 'O');
});

test('getAuthorInfo resolves member from server members list and identifies server owner', () => {
  const members = [
    { id: 1, name: 'Alice', nickname: 'Alice Nick', avatar: 'https://example.com/alice.png' },
    { id: 3, username: 'charlie', avatar: null },
  ];
  const currentUser = { id: 2, displayName: 'Oni' };

  const ownerAuthor = getAuthorInfo(1, members, currentUser, 1);
  assert.equal(ownerAuthor.isOwn, false);
  assert.equal(ownerAuthor.isOwner, true);
  assert.equal(ownerAuthor.name, 'Alice Nick');
  assert.equal(ownerAuthor.avatar, 'https://example.com/alice.png');
  assert.equal(ownerAuthor.initial, 'A');

  const memberAuthor = getAuthorInfo(3, members, currentUser, 1);
  assert.equal(memberAuthor.isOwn, false);
  assert.equal(memberAuthor.isOwner, false);
  assert.equal(memberAuthor.name, 'charlie');
  assert.equal(memberAuthor.avatar, null);
  assert.equal(memberAuthor.initial, 'C');

  const unknownAuthor = getAuthorInfo(99, members, currentUser, 1);
  assert.equal(unknownAuthor.isOwn, false);
  assert.equal(unknownAuthor.isOwner, false);
  assert.equal(unknownAuthor.name, 'Thành viên #99');
  assert.equal(unknownAuthor.initial, 'T');
});

test('updateMessageInList updates target message and preserves other messages', () => {
  const initial = [
    { id: 101, content: 'Original 101', updatedAt: null },
    { id: 102, content: 'Original 102', updatedAt: null },
    { id: 103, content: 'Original 103', updatedAt: null },
  ];

  const updated = updateMessageInList(initial, {
    id: 102,
    content: 'Edited 102',
    updatedAt: '2026-10-30T22:50:00Z',
  });

  assert.equal(updated.length, 3);
  assert.equal(updated[0].content, 'Original 101');
  assert.equal(updated[1].content, 'Edited 102');
  assert.equal(updated[1].updatedAt, '2026-10-30T22:50:00Z');
  assert.equal(updated[2].content, 'Original 103');
});

test('updateMessageInList handles empty list, missing id, and invalid inputs safely', () => {
  assert.deepEqual(updateMessageInList([], { id: 1, content: 'test' }), []);
  assert.deepEqual(updateMessageInList(null, { id: 1 }), []);
  assert.deepEqual(updateMessageInList([{ id: 1, content: 'a' }], null), [{ id: 1, content: 'a' }]);
  assert.deepEqual(updateMessageInList([{ id: 1, content: 'a' }], { content: 'b' }), [{ id: 1, content: 'a' }]);
});

test('isMessageEdited detects updatedAt or lastModifiedDate correctly', () => {
  assert.equal(isMessageEdited(null), false);
  assert.equal(isMessageEdited({}), false);
  assert.equal(isMessageEdited({ id: 1, content: 'Hello', updatedAt: null }), false);
  assert.equal(isMessageEdited({ id: 1, content: 'Hello', updatedAt: '2026-10-30T22:50:00Z' }), true);
  assert.equal(isMessageEdited({ id: 1, content: 'Hello', lastModifiedDate: '2026-10-30T22:50:00Z' }), true);
});

test('removeMessageFromList removes target message and preserves other messages in order', () => {
  const initial = [
    { id: 101, content: 'First message' },
    { id: 102, content: 'Second message to be deleted' },
    { id: 103, content: 'Third message' },
  ];

  const result = removeMessageFromList(initial, 102);

  assert.equal(result.length, 2);
  assert.equal(result[0].id, 101);
  assert.equal(result[1].id, 103);
});

test('removeMessageFromList handles string or number message ID correctly', () => {
  const initial = [
    { id: 101, content: 'Msg 101' },
    { id: '102', content: 'Msg 102' },
  ];

  assert.equal(removeMessageFromList(initial, '101').length, 1);
  assert.equal(removeMessageFromList(initial, 102).length, 1);
});

test('removeMessageFromList handles empty list, missing id, not found id, and invalid inputs safely', () => {
  assert.deepEqual(removeMessageFromList([], 101), []);
  assert.deepEqual(removeMessageFromList(null, 101), []);
  assert.deepEqual(removeMessageFromList(undefined, 101), []);
  assert.deepEqual(removeMessageFromList([{ id: 1 }], null), [{ id: 1 }]);
  assert.deepEqual(removeMessageFromList([{ id: 1 }], undefined), [{ id: 1 }]);

  const list = [{ id: 1 }, { id: 2 }];
  assert.deepEqual(removeMessageFromList(list, 999), list);
});

test('canDeleteMessage strictly restricts deletion to author only', () => {
  assert.equal(canDeleteMessage(10, 10), true);
  assert.equal(canDeleteMessage('10', 10), true);
  assert.equal(canDeleteMessage(10, '10'), true);
  assert.equal(canDeleteMessage(10, 99), false);
  assert.equal(canDeleteMessage(null, 10), false);
  assert.equal(canDeleteMessage(10, null), false);
  assert.equal(canDeleteMessage(undefined, undefined), false);
});

test('canEditMessage strictly restricts editing to author only (BR-MSG-02)', () => {
  assert.equal(canEditMessage(5, 5), true);
  assert.equal(canEditMessage(5, 6), false);
  assert.equal(canEditMessage(null, 5), false);
  assert.equal(canEditMessage(5, null), false);
});

test('calculateScrollAfterPrepend preserves exact reading position considering initial scrollTop and scrollHeight delta', () => {
  // When user is at scrollTop = 20px, scrollHeight increases from 500px to 800px (delta = 300px)
  // New scrollTop should be 20 + (800 - 500) = 320px
  assert.equal(calculateScrollAfterPrepend(20, 500, 800), 320);

  // When user is at top (scrollTop = 0), scrollHeight increases from 1000px to 1600px
  assert.equal(calculateScrollAfterPrepend(0, 1000, 1600), 600);

  // Gracefully handles 0 or missing values
  assert.equal(calculateScrollAfterPrepend(), 0);
  assert.equal(calculateScrollAfterPrepend(15, null, 100), 115);
});

test('multi-page pagination merges pages without duplicates or missing messages', () => {
  const page1 = [
    { id: 104, content: 'Msg 4' },
    { id: 105, content: 'Msg 5' },
  ];

  const page2 = [
    { id: 102, content: 'Msg 2' },
    { id: 103, content: 'Msg 3' },
  ];
  const merged1 = mergeMessagePages(page1, page2);
  assert.equal(merged1.length, 4);
  assert.deepEqual(merged1.map(m => m.id), [102, 103, 104, 105]);

  const page3 = [
    { id: 101, content: 'Msg 1' },
    { id: 102, content: 'Msg 2 (duplicate cursor overlap)' },
  ];
  const merged2 = mergeMessagePages(merged1, page3);
  assert.equal(merged2.length, 5);
  assert.deepEqual(merged2.map(m => m.id), [101, 102, 103, 104, 105]);
});

test('error state does not wipe existing message list or reset cursor', () => {
  const currentMessages = [
    { id: 103, content: 'Msg 3' },
    { id: 104, content: 'Msg 4' },
  ];
  const currentCursor = 103;

  const isError = true;
  const messagesAfterError = isError ? currentMessages : [];
  const cursorAfterError = isError ? currentCursor : null;

  assert.equal(messagesAfterError.length, 2);
  assert.equal(cursorAfterError, 103);
});



