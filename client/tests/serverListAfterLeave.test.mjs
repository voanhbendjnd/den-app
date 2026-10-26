import test from 'node:test';
import assert from 'node:assert/strict';
import {
  canLeaveServer,
  getLeaveErrorMessage,
  removeServerFromList,
  isServerInList,
} from '../src/utils/serverLeave.js';

test('removeServerFromList removes server by number or string ID', () => {
  const initial = [
    { id: 10, name: 'Server Ten' },
    { id: 20, name: 'Server Twenty' },
    { id: 30, name: 'Server Thirty' },
  ];

  const removedByNumber = removeServerFromList(initial, 20);
  assert.equal(removedByNumber.length, 2);
  assert.equal(isServerInList(removedByNumber, 20), false);
  assert.equal(isServerInList(removedByNumber, 10), true);
  assert.equal(isServerInList(removedByNumber, 30), true);

  const removedByString = removeServerFromList(initial, '10');
  assert.equal(removedByString.length, 2);
  assert.equal(isServerInList(removedByString, 10), false);
  assert.equal(isServerInList(removedByString, 20), true);
});

test('removeServerFromList returns copy of list if serverId is not found', () => {
  const initial = [
    { id: 10, name: 'Server Ten' },
  ];

  const result = removeServerFromList(initial, 999);
  assert.equal(result.length, 1);
  assert.equal(result[0].id, 10);
  assert.notEqual(result, initial); // Must be a new array reference
});

test('removeServerFromList handles null, undefined, or empty inputs gracefully', () => {
  assert.deepEqual(removeServerFromList(null, 10), []);
  assert.deepEqual(removeServerFromList(undefined, 10), []);
  assert.deepEqual(removeServerFromList([], 10), []);

  const list = [{ id: 10, name: 'Server Ten' }];
  assert.deepEqual(removeServerFromList(list, null), list);
  assert.deepEqual(removeServerFromList(list, undefined), list);
});

test('removeServerFromList preserves order and leaves empty array when only server is removed', () => {
  const single = [{ id: 42, name: 'Only Server' }];
  const empty = removeServerFromList(single, 42);
  assert.equal(empty.length, 0);

  const multiple = [
    { id: 1, name: 'First' },
    { id: 2, name: 'Second' },
    { id: 3, name: 'Third' },
  ];
  const remaining = removeServerFromList(multiple, 2);
  assert.deepEqual(remaining.map((s) => s.id), [1, 3]);
});

test('isServerInList returns true when server exists, false otherwise', () => {
  const servers = [
    { id: 10, name: 'Alpha' },
    { id: 20, name: 'Beta' },
  ];

  assert.equal(isServerInList(servers, 10), true);
  assert.equal(isServerInList(servers, '20'), true);
  assert.equal(isServerInList(servers, 30), false);
  assert.equal(isServerInList(null, 10), false);
  assert.equal(isServerInList(servers, null), false);
});

test('canLeaveServer enforces BR-SRV-01: owner cannot leave own server', () => {
  const server = { id: 10, name: 'Owner Guild', ownerId: 1 };
  const ownerUser = { id: 1, name: 'Owner' };
  const memberUser = { id: 2, name: 'Member' };

  const ownerResult = canLeaveServer(server, ownerUser);
  assert.equal(ownerResult.canLeave, false);
  assert.equal(ownerResult.isOwner, true);
  assert.equal(ownerResult.reason, 'Chủ sở hữu không thể tự rời Server. Hãy chuyển quyền sở hữu hoặc xóa Server.');

  const memberResult = canLeaveServer(server, memberUser);
  assert.equal(memberResult.canLeave, true);
  assert.equal(memberResult.isOwner, false);
  assert.equal(memberResult.reason, null);
});
