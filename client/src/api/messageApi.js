import axiosClient from './axiosClient';

const messageApi = {
  getMessages(roomId, params) {
    return axiosClient.get(`/rooms/${roomId}/messages`, { params });
  },

  sendMessage(roomId, data) {
    return axiosClient.post(`/rooms/${roomId}/messages`, data);
  },
};

export default messageApi;
