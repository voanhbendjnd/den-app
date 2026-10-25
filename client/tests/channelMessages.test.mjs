import test from 'node:test';
import assert from 'node:assert/strict';
import dayjs from 'dayjs';
import {
  sortMessagesChronological,
  mergeMessagePages,
  formatMessageTime,
  formatCompactTime,
  getAuthorInfo,
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
