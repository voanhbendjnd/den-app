import axiosClient from './axiosClient';

const serverApi = {
  discover({ q = '', page = 0, size = 20 } = {}, signal) {
    return axiosClient.get('/v1/servers/discover', { params: { q, page, size }, signal });
  },
  create(data) {
    return axiosClient.post('/v1/servers', data);
  },

  getAll() {
    return axiosClient.get('/v1/servers');
  },

  getById(id) {
    return axiosClient.get(`/v1/servers/${id}`);
  },

  update(id, data) {
    return axiosClient.put(`/v1/servers/${id}`, data);
  },

  delete(id) {
    return axiosClient.delete(`/v1/servers/${id}`);
  },

  join(serverId) {
    return axiosClient.post(`/v1/servers/${serverId}/join`);
  },

  joinByInvite(code) {
    return axiosClient.post(`/v1/servers/join-invite/${encodeURIComponent(code)}`);
  },

  getMembers(serverId) {
    return axiosClient.get(`/v1/servers/${serverId}/members`);
  },

  createInvite(serverId, data) {
    return axiosClient.post(`/v1/servers/${serverId}/invites`, data);
  },

  leave(serverId) {
    return axiosClient.post(`/v1/servers/${serverId}/leave`);
  },

  removeMember(serverId, userId) {
    return axiosClient.delete(`/v1/servers/${serverId}/members/${userId}`);
  },
};

export default serverApi;
