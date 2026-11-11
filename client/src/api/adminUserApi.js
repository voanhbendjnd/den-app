import axiosClient from './axiosClient';

const adminUserApi = {
  getAll(params) {
    return axiosClient.get('/v1/admin/users', { params });
  },

  getById(userId) {
    return axiosClient.get(`/v1/admin/users/${encodeURIComponent(userId)}`);
  },

  getOwnershipPreview(userId) {
    return axiosClient.get(`/v1/admin/users/${encodeURIComponent(userId)}/ownership-preview`);
  },

  create(data) {
    return axiosClient.post('/v1/admin/users', data);
  },

  update(userId, data) {
    return axiosClient.put(`/v1/admin/users/${encodeURIComponent(userId)}`, data);
  },

  softDelete(userId) {
    return axiosClient.delete(`/v1/admin/users/${encodeURIComponent(userId)}`);
  },

  restore(userId) {
    return axiosClient.patch(`/v1/admin/users/${encodeURIComponent(userId)}/restore`);
  },
};

export default adminUserApi;

