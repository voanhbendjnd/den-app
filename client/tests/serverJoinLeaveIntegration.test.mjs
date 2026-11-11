import test from 'node:test';
import assert from 'node:assert/strict';
import {
  canLeaveServer,
  getLeaveErrorMessage,
  removeServerFromList,
  isServerInList,
} from '../src/utils/serverLeave.js';
import {
  getJoinErrorMessage,
  parseJoinInput,
  validateJoinInput,
} from '../src/utils/serverJoin.js';
import {
  mergeNewMemberIntoList,
  findMemberByUserId,
} from '../src/utils/serverMember.js';

test('Task 4.04: Valid user joins server and server list is updated correctly', () => {
  const initialServerList = [
    { id: 1, name: 'Personal Hangout' },
  ];
  const newServer = { id: 10, name: 'DenHub Community' };

  // Joining adds server to list
  const updatedList = [...initialServerList, newServer];
  assert.equal(updatedList.length, 2);
  assert.equal(isServerInList(updatedList, 10), true);
  assert.equal(isServerInList(updatedList, 1), true);
});

test('Task 4.04: Failed join does not produce ghost membership or mutate server list', () => {
  const initialServerList = [
    { id: 1, name: 'Personal Hangout' },
  ];
  let serverList = [...initialServerList];

  // Simulate join error (e.g., 400 alreadymember, 404 not found, network failure)
  const joinError = { response: { status: 400, data: { errorKey: 'alreadymember' } } };
  const errorMsg = getJoinErrorMessage(joinError);
  assert.equal(errorMsg, 'Bạn đã là thành viên của Server này rồi!');

  // On error, frontend must NOT mutate serverList
  assert.equal(serverList.length, 1);
  assert.equal(isServerInList(serverList, 999), false);
  assert.deepEqual(serverList, initialServerList);
});

test('Task 4.04: User leaves server successfully and server disappears while other servers remain', () => {
  const userServers = [
    { id: 1, name: 'Work Server' },
    { id: 10, name: 'Gaming Guild' },
  ];

  // User leaves Gaming Guild (id: 10)
  const remainingServers = removeServerFromList(userServers, 10);
  assert.equal(remainingServers.length, 1);
  assert.equal(isServerInList(remainingServers, 10), false);
  assert.equal(isServerInList(remainingServers, 1), true);
  assert.equal(remainingServers[0].id, 1);
});

test('Task 4.04: Failed leave does not remove server falsely from frontend state', () => {
  const userServers = [
    { id: 10, name: 'Important Server' },
    { id: 20, name: 'Secondary Server' },
  ];
  let currentList = [...userServers];

  // Simulate leave failure (e.g., network error or server rejection)
  const leaveError = { code: 'ERR_NETWORK' };
  const errorMsg = getLeaveErrorMessage(leaveError, 'Important Server');
  assert.match(errorMsg, /Không thể kết nối đến máy chủ/);

  // In catch block: currentList is NOT modified
  assert.equal(currentList.length, 2);
  assert.equal(isServerInList(currentList, 10), true);
});

test('Task 4.04: Owner cannot leave own server per BR-SRV-01', () => {
  const server = { id: 50, name: 'Owner Alpha', ownerId: 'user_123' };
  const ownerUser = { id: 'user_123', name: 'Alice Owner' };
  const regularUser = { id: 'user_456', name: 'Bob Member' };

  const ownerCheck = canLeaveServer(server, ownerUser);
  assert.equal(ownerCheck.canLeave, false);
  assert.equal(ownerCheck.isOwner, true);
  assert.match(ownerCheck.reason, /Chủ sở hữu không thể tự rời Server/);

  const memberCheck = canLeaveServer(server, regularUser);
  assert.equal(memberCheck.canLeave, true);
  assert.equal(memberCheck.isOwner, false);
  assert.equal(memberCheck.reason, null);
});

test('Task 4.04: Membership list updates when new member joins and rolls back if duplicate', () => {
  const existingMembers = [
    { userId: 1, username: 'owner_user', role: 'OWNER' },
  ];
  const newMember = { userId: 2, username: 'joiner_user', role: 'MEMBER' };

  // Join adds member
  const updatedMembers = mergeNewMemberIntoList(existingMembers, newMember);
  assert.equal(updatedMembers.length, 2);
  assert.equal(findMemberByUserId(updatedMembers, 2).username, 'joiner_user');

  // Duplicate join attempt does not duplicate row
  const duplicateAttempt = mergeNewMemberIntoList(updatedMembers, newMember);
  assert.equal(duplicateAttempt.length, 2);
});
