import axiosClient from './axiosClient';

const messageApi = {
  getMessages(channelId, params) {
    return axiosClient.get(`/channels/${channelId}/messages`, { params });
  },

  sendMessage(channelId, data) {
    return axiosClient.post(`/channels/${channelId}/messages`, data);
  },

  updateMessage(messageId, data) {
    return axiosClient.patch(`/messages/${messageId}`, data);
  },

  deleteMessage(messageId) {
    return axiosClient.delete(`/messages/${messageId}`);
  },
};

export default messageApi;
