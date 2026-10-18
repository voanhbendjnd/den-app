import axiosClient from './axiosClient';

const channelApi = {
  create: (data) => axiosClient.post('/v1/channels', data),
  getByServerId: (serverId) => axiosClient.get(`/v1/servers/${serverId}/channels`),
  update: (channelId, data) => axiosClient.put(`/v1/channels/${channelId}`, data),
};

export default channelApi;
