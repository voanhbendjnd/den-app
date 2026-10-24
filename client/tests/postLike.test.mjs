import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Pure helper function implementing optimistic like state transitions.
 */
function applyOptimisticLike(posts, postId) {
  let previousPost = null;
  const updatedPosts = posts.map((post) => {
    if (post.id !== postId) return post;
    previousPost = { ...post };
    const isLiked = post.isLiked ?? false;
    const currentLikes = post.likeCounts ?? 0;
    return {
      ...post,
      isLiked: !isLiked,
      likeCounts: isLiked ? Math.max(0, currentLikes - 1) : currentLikes + 1,
    };
  });
  return { updatedPosts, previousPost };
}

/**
 * Pure helper function implementing server reconciliation after like response.
 */
function applyServerLikeResponse(posts, postId, serverResponse) {
  const data = serverResponse?.data ?? serverResponse;
  if (!data) return posts;
  return posts.map((post) => {
    if (post.id !== postId) return post;
    return {
      ...post,
      isLiked: typeof data.liked === 'boolean' ? data.liked : post.isLiked,
      likeCounts: typeof data.totalLikes === 'number' ? data.totalLikes : post.likeCounts,
    };
  });
}

/**
 * Pure helper function for rollback when API call fails.
 */
function applyRollback(posts, postId, previousPost) {
  if (!previousPost) return posts;
  return posts.map((post) => (post.id === postId ? previousPost : post));
}

test('applyOptimisticLike increments likeCounts and sets isLiked to true when currently unliked', () => {
  const initialPosts = [
    { id: 1, content: 'First post', likeCounts: 3, isLiked: false },
    { id: 2, content: 'Second post', likeCounts: 0, isLiked: false },
  ];

  const { updatedPosts, previousPost } = applyOptimisticLike(initialPosts, 1);

  assert.equal(previousPost.likeCounts, 3);
  assert.equal(previousPost.isLiked, false);

  const target = updatedPosts.find((p) => p.id === 1);
  assert.equal(target.likeCounts, 4);
  assert.equal(target.isLiked, true);

  const untouched = updatedPosts.find((p) => p.id === 2);
  assert.equal(untouched.likeCounts, 0);
  assert.equal(untouched.isLiked, false);
});

test('applyOptimisticLike decrements likeCounts and sets isLiked to false when currently liked', () => {
  const initialPosts = [
    { id: 1, content: 'First post', likeCounts: 5, isLiked: true },
  ];

  const { updatedPosts, previousPost } = applyOptimisticLike(initialPosts, 1);

  assert.equal(previousPost.likeCounts, 5);
  assert.equal(previousPost.isLiked, true);

  const target = updatedPosts.find((p) => p.id === 1);
  assert.equal(target.likeCounts, 4);
  assert.equal(target.isLiked, false);
});

test('applyOptimisticLike does not decrement likeCounts below zero', () => {
  const initialPosts = [
    { id: 1, content: 'First post', likeCounts: 0, isLiked: true },
  ];

  const { updatedPosts } = applyOptimisticLike(initialPosts, 1);
  const target = updatedPosts.find((p) => p.id === 1);
  assert.equal(target.likeCounts, 0);
  assert.equal(target.isLiked, false);
});

test('applyServerLikeResponse reconciles server state with totalLikes and liked flag', () => {
  const posts = [
    { id: 1, content: 'First post', likeCounts: 4, isLiked: true },
  ];

  const serverResponse = {
    code: 200,
    message: 'Success',
    data: {
      liked: true,
      totalLikes: 10,
    },
  };

  const reconciled = applyServerLikeResponse(posts, 1, serverResponse);
  const target = reconciled.find((p) => p.id === 1);
  assert.equal(target.likeCounts, 10);
  assert.equal(target.isLiked, true);
});

test('applyRollback restores previous post state upon error', () => {
  const currentPosts = [
    { id: 1, content: 'First post', likeCounts: 4, isLiked: true },
  ];
  const previousPost = { id: 1, content: 'First post', likeCounts: 3, isLiked: false };

  const rolledBack = applyRollback(currentPosts, 1, previousPost);
  const target = rolledBack.find((p) => p.id === 1);
  assert.equal(target.likeCounts, 3);
  assert.equal(target.isLiked, false);
});
