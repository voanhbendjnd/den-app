import axiosClient from './axiosClient';

const notificationApi = {
  getNotifications(params) {
    return axiosClient.get('/notifications', { params });
  },

  markAsRead(id) {
    return axiosClient.patch(`/notifications/${id}/read`);
  },

  markAllAsRead() {
    return axiosClient.patch('/notifications/read-all');
  },
};

export default notificationApi;
