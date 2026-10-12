import axiosClient from './axiosClient';

const authApi = {
  login(credentials) {
    return axiosClient.post('/login', credentials);
  },

  register(data) {
    return axiosClient.post('/register', data);
  },
};

export default authApi;
