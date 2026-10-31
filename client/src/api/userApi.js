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

  /**
   * Cập nhật thông tin hồ sơ người dùng hiện tại
   * @param {Object} data { name?: string, username?: string, avatar?: string }
   * @returns {Promise<any>}
   */
  updateProfile(data) {
    return axiosClient.put('/v1/users/profile', data);
  },

  /**
   * Alias cập nhật thông tin hồ sơ người dùng hiện tại (/me)
   * @param {Object} data { name?: string, username?: string, avatar?: string }
   * @returns {Promise<any>}
   */
  /**
   * Đổi mật khẩu người dùng hiện tại
   * @param {Object} data { currentPassword: string, newPassword: string }
   * @returns {Promise<any>}
   */
  changePassword(data) {
    return axiosClient.post('/v1/users/change-password', data);
  },

  updateMe(data) {
    return axiosClient.put('/v1/users/me', data);
  },
};

export default userApi;
