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

  getProfile() {
    return axiosClient.get('/v1/users/profile');
  },

  getMe() {
    return axiosClient.get('/v1/users/profile');
  },
};

export default authApi;
