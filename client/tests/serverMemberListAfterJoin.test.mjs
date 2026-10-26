import test from 'node:test';
import assert from 'node:assert/strict';
import {
  findMemberByUserId,
  mergeNewMemberIntoList,
  getMemberCountDisplay,
  formatMemberDisplayName,
} from '../src/utils/serverMember.js';

test('findMemberByUserId returns correct member matching number or string id', () => {
  const members = [
    { id: 101, userId: 1, name: 'Alice', role: 'OWNER' },
    { id: 102, userId: 2, name: 'Bob', role: 'MEMBER' },
    { id: 103, userId: 3, name: 'Charlie', role: 'MEMBER' },
  ];

  const foundNumber = findMemberByUserId(members, 2);
  assert.equal(foundNumber?.name, 'Bob');

  const foundString = findMemberByUserId(members, '3');
  assert.equal(foundString?.name, 'Charlie');

  const notFound = findMemberByUserId(members, 999);
  assert.equal(notFound, null);
});

test('findMemberByUserId handles null, undefined, or non-array inputs gracefully', () => {
  assert.equal(findMemberByUserId(null, 1), null);
  assert.equal(findMemberByUserId(undefined, 1), null);
  assert.equal(findMemberByUserId('invalid', 1), null);
  assert.equal(findMemberByUserId([], null), null);
  assert.equal(findMemberByUserId([], undefined), null);
});

test('mergeNewMemberIntoList appends a newly joined member to existing list', () => {
  const initialMembers = [
    { id: 101, userId: 1, name: 'Alice', role: 'OWNER' },
  ];

  const newJoiner = {
    id: 102,
    userId: 2,
    username: 'bob',
    name: 'Bob Joiner',
    avatar: 'https://example.com/bob.png',
  };

  const updated = mergeNewMemberIntoList(initialMembers, newJoiner, { id: 10, ownerId: 1 });
  assert.equal(updated.length, 2);
  assert.equal(updated[1].userId, 2);
  assert.equal(updated[1].role, 'MEMBER');
  assert.equal(updated[1].name, 'Bob Joiner');
});

test('mergeNewMemberIntoList prevents duplicate entries when user already exists', () => {
  const existingMembers = [
    { id: 101, userId: 1, name: 'Alice', role: 'OWNER' },
    { id: 102, userId: 2, name: 'Bob', role: 'MEMBER' },
  ];

  const duplicateJoiner = {
    id: 202,
    userId: 2,
    name: 'Bob Duplicate',
  };

  const updated = mergeNewMemberIntoList(existingMembers, duplicateJoiner);
  assert.equal(updated.length, 2);
  assert.equal(updated[1].name, 'Bob');
});

test('mergeNewMemberIntoList assigns OWNER role if joined user is server owner', () => {
  const server = { id: 10, ownerId: 1 };
  const ownerJoiner = {
    id: 101,
    userId: 1,
    name: 'Alice Owner',
  };

  const updated = mergeNewMemberIntoList([], ownerJoiner, server);
  assert.equal(updated.length, 1);
  assert.equal(updated[0].role, 'OWNER');
});

test('mergeNewMemberIntoList handles null or invalid newMember gracefully', () => {
  const list = [{ id: 1, userId: 1, name: 'Alice' }];
  assert.deepEqual(mergeNewMemberIntoList(list, null), list);
  assert.deepEqual(mergeNewMemberIntoList(list, undefined), list);
  assert.deepEqual(mergeNewMemberIntoList(list, {}), list);
});

test('getMemberCountDisplay formats member count label accurately', () => {
  assert.equal(getMemberCountDisplay([]), 'Thành viên (0)');
  assert.equal(getMemberCountDisplay(null), 'Thành viên (0)');
  assert.equal(getMemberCountDisplay(undefined), 'Thành viên (0)');
  assert.equal(getMemberCountDisplay([{ id: 1 }]), 'Thành viên (1)');
  assert.equal(getMemberCountDisplay([{ id: 1 }, { id: 2 }, { id: 3 }]), 'Thành viên (3)');
});

test('formatMemberDisplayName prioritizes nickname over name, username, and default', () => {
  const full = { nickname: 'CoolNick', name: 'Cool Name', username: 'cooluser' };
  assert.equal(formatMemberDisplayName(full), 'CoolNick');

  const noNick = { name: 'Cool Name', username: 'cooluser' };
  assert.equal(formatMemberDisplayName(noNick), 'Cool Name');

  const usernameOnly = { username: 'cooluser' };
  assert.equal(formatMemberDisplayName(usernameOnly), 'cooluser');

  const empty = {};
  assert.equal(formatMemberDisplayName(empty), 'Thành viên');
  assert.equal(formatMemberDisplayName(null), 'Thành viên');
});
