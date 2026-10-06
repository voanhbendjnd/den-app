import axiosClient from './axiosClient';

const memberApi = {
  getRoomMembers(roomId, params) {
    return axiosClient.get(`/rooms/${roomId}/members`, { params });
  },

  addMember(roomId, data) {
    return axiosClient.post(`/rooms/${roomId}/members`, data);
  },

  removeMember(roomId, userId) {
    return axiosClient.delete(`/rooms/${roomId}/members/${userId}`);
  },

  updateMemberRole(roomId, userId, data) {
    return axiosClient.put(`/rooms/${roomId}/members/${userId}/role`, data);
  },

  // Admin
  getAllUsers(params) {
    return axiosClient.get('/admin/users', { params });
  },

  updateUser(userId, data) {
    return axiosClient.put(`/admin/users/${userId}`, data);
  },
};

export default memberApi;
