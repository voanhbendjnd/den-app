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
  getFileUrl: (folder, filename) => {
    if (!filename) return '';
    if (filename.startsWith('http')) return filename;
    return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'}/v1/files/${folder}/${filename}`;
  },
};

export default fileApi;
