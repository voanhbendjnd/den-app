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
};

export default friendApi;
