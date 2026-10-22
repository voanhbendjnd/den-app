import axiosClient from './axiosClient';

const serverApi = {
  create: (data) => axiosClient.post('/v1/servers', data),
  getAll: () => axiosClient.get('/v1/servers'),
  getById: (id) => axiosClient.get(`/v1/servers/${id}`),
  update: (id, data) => axiosClient.put(`/v1/servers/${id}`, data),
  delete: (id) => axiosClient.delete(`/v1/servers/${id}`),
  join: (serverId) => axiosClient.post(`/v1/servers/${serverId}/join`),
  joinByInvite: (code) => axiosClient.post(`/v1/servers/join-invite/${encodeURIComponent(code)}`),
  getMembers: (serverId) => axiosClient.get(`/v1/servers/${serverId}/members`),
  createInvite: (serverId, data) => axiosClient.post(`/v1/servers/${serverId}/invites`, data),
};

export default serverApi;
