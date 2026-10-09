import axiosClient from './axiosClient';

const serverApi = {
  create: (data) => axiosClient.post('/v1/servers', data),
  getAll: () => axiosClient.get('/v1/servers'),
  getById: (id) => axiosClient.get(`/v1/servers/${id}`),
};

export default serverApi;
