/**
 * postApi.js
 * API client cho tính năng Bài viết (Posts) & Bảng tin (Feed) bạn bè.
 */
import axiosClient from './axiosClient';

const postApi = {
  /**
   * Lấy danh sách bài viết từ bạn bè (Cursor pagination theo date và id).
   * POST /api/posts/all-friends
   * @param {number} size - số lượng bài viết mỗi trang (mặc định 10)
   * @param {string|null} cursorDate - ISO-8601 createdDate của bài viết cuối cùng
   * @param {number|null} cursorId - ID của bài viết cuối cùng
   * @returns {Promise<{ result, cursorDate, cursorId, hasMore }>}
   */
  getAllPostsFromFriends: (size = 10, cursorDate = null, cursorId = null) =>
    axiosClient.post('/posts/all-friends', { size, cursorDate, cursorId }),

  /**
   * Thích hoặc bỏ thích một bài viết.
   * POST /api/posts/{postId}/like
   * @param {number|string} postId - ID của bài viết
   * @returns {Promise<{ code, message, data: { postId, liked, likeCounts } }>}
   */
  toggleLike: (postId) => axiosClient.post(`/posts/${postId}/like`),
};

export default postApi;

