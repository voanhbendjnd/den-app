import axiosClient from './axiosClient';

const fileApi = {
  upload: (file, folder = 'servers') => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);
    return axiosClient.post('/v1/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
};

export default fileApi;
