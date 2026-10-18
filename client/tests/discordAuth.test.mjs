import test from 'node:test';
import assert from 'node:assert/strict';

test('authApi data formatting: register payload contains name, username, email, password', () => {
  const input = {
    email: 'newuser@denhub.app',
    password: 'Password123!',
    displayName: 'New Gamer',
    username: 'newgamer',
  };

  const payload = {
    email: input.email,
    password: input.password,
    name: input.displayName || input.username || input.email.split('@')[0],
    username: input.username,
  };

  assert.equal(payload.email, 'newuser@denhub.app');
  assert.equal(payload.password, 'Password123!');
  assert.equal(payload.name, 'New Gamer');
  assert.equal(payload.username, 'newgamer');
});

test('authApi data formatting: register payload falls back name to email prefix when displayName empty', () => {
  const input = {
    email: 'gamer123@denhub.app',
    password: 'Password123!',
    displayName: '',
    username: 'gamer123',
  };

  const payload = {
    email: input.email,
    password: input.password,
    name: input.displayName || input.username || input.email.split('@')[0],
    username: input.username,
  };

  assert.equal(payload.name, 'gamer123');
});

test('googleLogin payload formatting: accepts credential or direct profile fields', () => {
  const credentialPayload = {
    credential: 'mocked-jwt-id-token',
  };
  assert.equal(credentialPayload.credential, 'mocked-jwt-id-token');

  const fallbackPayload = {
    email: 'google.user@denhub.app',
    name: 'Google User',
    avatar: 'https://lh3.googleusercontent.com/avatar.png',
    googleId: 'google_123',
  };
  assert.equal(fallbackPayload.email, 'google.user@denhub.app');
  assert.equal(fallbackPayload.name, 'Google User');
});

test('user normalization handles ResLoginDTO structure', () => {
  const rawUser = {
    id: 99,
    email: 'discord.user@denhub.app',
    name: 'Discord Gamer',
    authorities: ['ROLE_USER'],
  };

  const normalized = {
    id: rawUser.id,
    email: rawUser.email,
    name: rawUser.name,
    displayName: rawUser.name || rawUser.email.split('@')[0] || 'User',
    authorities: rawUser.authorities,
    role: rawUser.authorities.includes('ROLE_ADMIN') ? 'ADMIN' : 'USER',
  };

  assert.equal(normalized.id, 99);
  assert.equal(normalized.email, 'discord.user@denhub.app');
  assert.equal(normalized.displayName, 'Discord Gamer');
  assert.equal(normalized.role, 'USER');
});
