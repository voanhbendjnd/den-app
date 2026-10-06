import axiosClient from './axiosClient';

const authApi = {
  login(credentials) {
    return axiosClient.post('/auth/login', credentials);
  },

  register(data) {
    return axiosClient.post('/auth/register', data);
  },

  getMe() {
    return axiosClient.get('/users/me');
  },
};

export default authApi;
