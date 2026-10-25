import axiosClient from './axiosClient';

const userApi = {
  /**
   * Lấy thông tin hồ sơ của người dùng hiện tại
   * @returns {Promise<any>}
   */
  getProfile() {
    return axiosClient.get('/v1/users/profile');
  },

  /**
   * Alias lấy thông tin hồ sơ người dùng hiện tại (/me)
   * @returns {Promise<any>}
   */
  getMe() {
    return axiosClient.get('/v1/users/me');
  },
};

export default userApi;
