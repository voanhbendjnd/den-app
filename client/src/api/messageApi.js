import axiosClient from './axiosClient';

const messageApi = {
  getMessages(channelId, params) {
    return axiosClient.get(`/channels/${channelId}/messages`, { params });
  },

  sendMessage(channelId, data) {
    return axiosClient.post(`/channels/${channelId}/messages`, data);
  },
};

export default messageApi;
