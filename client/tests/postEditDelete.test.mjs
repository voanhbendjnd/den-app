import test from 'node:test';
import assert from 'node:assert/strict';

const MAX_CONTENT_LENGTH = 120;
const VALID_PRIVACIES = ['PUBLIC', 'FRIENDS_ONLY', 'PRIVATE'];

/**
 * Pure helper function implementing edit payload validation.
 */
function validateEditPostPayload(payload) {
  if (!payload) {
    return { valid: false, error: 'Payload is required' };
  }

  const content = payload.content ? payload.content.trim() : '';
  const mediaUrl = payload.mediaUrl ? payload.mediaUrl.trim() : '';

  if (!content && !mediaUrl) {
    return { valid: false, error: 'Post must contain text content or media' };
  }

  if (content.length > MAX_CONTENT_LENGTH) {
    return { valid: false, error: `Content cannot exceed ${MAX_CONTENT_LENGTH} characters` };
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

/**
 * Pure helper function implementing optimistic edit state updates.
 */
function applyOptimisticEdit(posts, postId, updatedData) {
  return posts.map((post) => (post.id === postId ? { ...post, ...updatedData } : post));
}

/**
 * Pure helper function implementing optimistic delete state updates.
 */
function applyOptimisticDelete(posts, postId) {
  const previousPosts = [...posts];
  const updatedPosts = posts.filter((post) => post.id !== postId);
  return { updatedPosts, previousPosts };
}

/**
 * Pure helper function implementing delete rollback on failure.
 */
function applyDeleteRollback(previousPosts) {
  return [...previousPosts];
}

test('validateEditPostPayload rejects empty content and empty media', () => {
  const result = validateEditPostPayload({ content: '   ', mediaUrl: '' });
  assert.equal(result.valid, false);
  assert.equal(result.error, 'Post must contain text content or media');
});

test('validateEditPostPayload accepts valid text within 120 characters', () => {
  const result = validateEditPostPayload({ content: 'Updated status message!', privacy: 'FRIENDS_ONLY' });
  assert.equal(result.valid, true);
  assert.equal(result.normalized.content, 'Updated status message!');
  assert.equal(result.normalized.privacy, 'FRIENDS_ONLY');
});

test('validateEditPostPayload rejects text longer than 120 characters', () => {
  const result = validateEditPostPayload({ content: 'x'.repeat(121) });
  assert.equal(result.valid, false);
  assert.match(result.error, /cannot exceed 120/);
});

test('validateEditPostPayload normalizes privacy case and defaults unknown to PUBLIC', () => {
  const res1 = validateEditPostPayload({ content: 'Hi', privacy: 'friends_only' });
  assert.equal(res1.valid, true);
  assert.equal(res1.normalized.privacy, 'FRIENDS_ONLY');

  const res2 = validateEditPostPayload({ content: 'Hi', privacy: 'unknown' });
  assert.equal(res2.valid, true);
  assert.equal(res2.normalized.privacy, 'PUBLIC');
});

test('applyOptimisticEdit replaces fields on target post and preserves other posts', () => {
  const initialPosts = [
    { id: 1, content: 'First post', privacy: 'PUBLIC' },
    { id: 2, content: 'Second post', privacy: 'PUBLIC' },
  ];

  const updated = applyOptimisticEdit(initialPosts, 1, {
    content: 'Edited first post',
    privacy: 'PRIVATE',
  });

  const target = updated.find((p) => p.id === 1);
  assert.equal(target.content, 'Edited first post');
  assert.equal(target.privacy, 'PRIVATE');

  const untouched = updated.find((p) => p.id === 2);
  assert.equal(untouched.content, 'Second post');
});

test('applyOptimisticDelete removes target post from list and captures previous list', () => {
  const initialPosts = [
    { id: 1, content: 'First post' },
    { id: 2, content: 'Second post' },
  ];

  const { updatedPosts, previousPosts } = applyOptimisticDelete(initialPosts, 1);

  assert.equal(updatedPosts.length, 1);
  assert.equal(updatedPosts[0].id, 2);
  assert.equal(previousPosts.length, 2);
});

test('applyDeleteRollback restores original post list upon failure', () => {
  const initialPosts = [
    { id: 1, content: 'First post' },
    { id: 2, content: 'Second post' },
  ];

  const { updatedPosts, previousPosts } = applyOptimisticDelete(initialPosts, 1);
  assert.equal(updatedPosts.length, 1);

  const restored = applyDeleteRollback(previousPosts);
  assert.equal(restored.length, 2);
  assert.equal(restored[0].id, 1);
  assert.equal(restored[1].id, 2);
});
