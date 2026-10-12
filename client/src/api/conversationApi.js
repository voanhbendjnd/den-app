/**
 * conversationApi.js
 * API client cho tính năng Direct Message (hội thoại 1-1).
 */
import axiosClient from './axiosClient';

const BASE = '/v1/conversations';

const conversationApi = {
  /**
   * Lấy danh sách tất cả conversations của user hiện tại.
   * GET /api/v1/conversations
   * @returns {Promise<ConversationResponseDTO[]>}
   */
  getAll: () => axiosClient.get(BASE),

  /**
   * Lấy lịch sử tin nhắn của một conversation (có phân trang).
   * GET /api/v1/conversations/{conversationId}/messages
   * @param {number} conversationId
   * @param {number} page   - 0-indexed
   * @param {number} size   - mặc định 30
   * @returns {Promise<Page<MessageHistoryDTO>>}
   */
  getMessages: (conversationId, page = 0, size = 30) =>
    axiosClient.get(`${BASE}/${conversationId}/messages`, { params: { page, size } }),
};

export default conversationApi;
