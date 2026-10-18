import axiosClient from './axiosClient';

const authApi = {
  login(credentials) {
    return axiosClient.post('/login', credentials);
  },

  register(data) {
    return axiosClient.post('/register', data);
  },

  googleLogin(data) {
    return axiosClient.post('/auth/google', data);
  },
};

export default authApi;
