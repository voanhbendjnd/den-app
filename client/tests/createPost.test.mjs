import test from 'node:test';
import assert from 'node:assert/strict';

const MAX_POST_LENGTH = 120;
const VALID_PRIVACIES = ['PUBLIC', 'FRIENDS_ONLY', 'PRIVATE'];

function validatePostPayload(payload) {
  if (!payload) {
    return { valid: false, error: 'Payload is required' };
  }

  const content = payload.content ? payload.content.trim() : '';
  const mediaUrl = payload.mediaUrl ? payload.mediaUrl.trim() : '';

  if (!content && !mediaUrl) {
    return { valid: false, error: 'Post must contain text content or media' };
  }

  if (content.length > MAX_POST_LENGTH) {
    return { valid: false, error: `Content cannot exceed ${MAX_POST_LENGTH} characters` };
  }

  if (mediaUrl.length > 1000) {
    return { valid: false, error: 'Media URL cannot exceed 1000 characters' };
  }

  const rawPrivacy = payload.privacy ? payload.privacy.trim().toUpperCase() : 'PUBLIC';
  const privacy = VALID_PRIVACIES.includes(rawPrivacy) ? rawPrivacy : 'PUBLIC';

  return {
    valid: true,
    normalized: {
      content: content || null,
      mediaUrl: mediaUrl || null,
      privacy,
    },
  };
}

test('validatePostPayload rejects empty content and empty media', () => {
  const result = validatePostPayload({ content: '   ', mediaUrl: '' });
  assert.equal(result.valid, false);
  assert.equal(result.error, 'Post must contain text content or media');
});

test('validatePostPayload accepts valid text within 120 characters', () => {
  const result = validatePostPayload({ content: 'Hello DenHub friends!', privacy: 'PUBLIC' });
  assert.equal(result.valid, true);
  assert.equal(result.normalized.content, 'Hello DenHub friends!');
  assert.equal(result.normalized.privacy, 'PUBLIC');
});

test('validatePostPayload rejects text longer than 120 characters', () => {
  const result = validatePostPayload({ content: 'a'.repeat(121) });
  assert.equal(result.valid, false);
  assert.match(result.error, /cannot exceed 120/);
});

test('validatePostPayload accepts media only without text', () => {
  const result = validatePostPayload({ content: '', mediaUrl: 'https://img.com/pic.png' });
  assert.equal(result.valid, true);
  assert.equal(result.normalized.mediaUrl, 'https://img.com/pic.png');
  assert.equal(result.normalized.content, null);
});

test('validatePostPayload normalizes case-insensitive privacy to uppercase and defaults to PUBLIC', () => {
  const res1 = validatePostPayload({ content: 'Test', privacy: 'friends_only' });
  assert.equal(res1.valid, true);
  assert.equal(res1.normalized.privacy, 'FRIENDS_ONLY');

  const res2 = validatePostPayload({ content: 'Test', privacy: 'unknown' });
  assert.equal(res2.valid, true);
  assert.equal(res2.normalized.privacy, 'PUBLIC');

  const res3 = validatePostPayload({ content: 'Test', privacy: null });
  assert.equal(res3.valid, true);
  assert.equal(res3.normalized.privacy, 'PUBLIC');
});
