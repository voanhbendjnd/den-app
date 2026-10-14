/**
 * friendApi.js
 * API client cho tính năng tìm kiếm user và gửi lời mời kết bạn.
 */
import axiosClient from './axiosClient';

const friendApi = {
  /**
   * Tìm kiếm người dùng theo username.
   * GET /api/users/search?username={q}
   * @param {string} username
   * @returns {Promise<UsernameProjection[]>}
   */
  searchUsers: (username) =>
    axiosClient.get('/users/search', { params: { username } }),

  /**
   * Gửi lời mời kết bạn.
   * POST /api/friendships/requests
   * @param {number} friendId - ID của người muốn kết bạn
   * @returns {Promise<void>}
   */
  sendFriendRequest: (friendId) =>
    axiosClient.post('/friendships/requests', { friendId }),

  /**
   * Lấy danh sách lời mời kết bạn nhận được (cursor pagination theo thời gian).
   * POST /api/friendships/friend-requests
   * @param {number} size - số lượng mỗi trang
   * @param {string|null} cursor - ISO-8601 lastModifiedDate của item cuối cùng
   * @returns {Promise<{ result, nextCursor, hasMore }>}
   */
  getFriendRequests: (size = 20, cursor = null) =>
    axiosClient.post('/friendships/friend-requests', { size, cursor }),

  /**
   * Chấp nhận lời mời kết bạn.
   * POST /api/friendships/accept-friend-request
   * @param {number} friendId - ID của người đã gửi lời mời
   * @returns {Promise<void>}
   */
  acceptFriendRequest: (friendId) =>
    axiosClient.post('/friendships/accept-friend-request', { friendId }),

  /**
   * Hủy hoặc từ chối lời mời kết bạn.
   * POST /api/friendships/remove-friend-request
   * @param {number} friendId - ID của người liên quan đến lời mời
   * @returns {Promise<void>}
   */
  removeFriendRequest: (friendId) =>
    axiosClient.post('/friendships/remove-friend-request', { friendId }),

  /**
   * Lấy danh sách tất cả bạn bè đã kết bạn (cursor pagination theo thời gian).
   * POST /api/friendships/all-friend
   * @param {number} size - số lượng mỗi trang
   * @param {string|null} cursor - ISO-8601 lastModifiedDate của item cuối cùng
   * @returns {Promise<{ result, nextCursor, hasMore }>}
   */
  getAllFriends: (size = 20, cursor = null) =>
    axiosClient.post('/friendships/all-friend', { size, cursor }),

  /**
   * Hủy kết bạn (unfriend).
   * POST /api/friendships/remove-friend
   * @param {number} friendId - ID của người bạn muốn hủy kết bạn
   * @returns {Promise<void>}
   */
  removeFriend: (friendId) =>
    axiosClient.post('/friendships/remove-friend', { friendId }),
};

export default friendApi;
