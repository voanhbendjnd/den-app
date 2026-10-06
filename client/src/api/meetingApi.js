import axiosClient from './axiosClient';

const meetingApi = {
  getMeetings(params) {
    return axiosClient.get('/meetings', { params });
  },

  getMeetingById(id) {
    return axiosClient.get(`/meetings/${id}`);
  },

  createMeeting(data) {
    return axiosClient.post('/meetings', data);
  },

  updateMeeting(id, data) {
    return axiosClient.put(`/meetings/${id}`, data);
  },

  deleteMeeting(id) {
    return axiosClient.delete(`/meetings/${id}`);
  },
};

export default meetingApi;
