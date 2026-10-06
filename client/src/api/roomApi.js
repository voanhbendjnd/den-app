import axiosClient from './axiosClient';

const roomApi = {
  getRooms(params) {
    return axiosClient.get('/rooms', { params });
  },

  getRoomBySlug(slug) {
    return axiosClient.get(`/rooms/slug/${slug}`);
  },

  getRoomById(id) {
    return axiosClient.get(`/rooms/${id}`);
  },

  createRoom(data) {
    return axiosClient.post('/rooms', data);
  },

  updateRoom(id, data) {
    return axiosClient.put(`/rooms/${id}`, data);
  },

  deleteRoom(id) {
    return axiosClient.delete(`/rooms/${id}`);
  },

  // Admin
  getAllRooms(params) {
    return axiosClient.get('/admin/rooms', { params });
  },
};

export default roomApi;
