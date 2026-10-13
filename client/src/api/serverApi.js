import axiosClient from './axiosClient';

const serverApi = {
  create: (data) => axiosClient.post('/v1/servers', data),
  getAll: () => axiosClient.get('/v1/servers'),
  getById: (id) => axiosClient.get(`/v1/servers/${id}`),
  join: (serverId) => axiosClient.post(`/v1/servers/${serverId}/join`),
};

export default serverApi;
