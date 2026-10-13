import axiosClient from './axiosClient';

const channelApi = {
  create: (data) => axiosClient.post('/v1/channels', data),
  getByServerId: (serverId) => axiosClient.get(`/v1/servers/${serverId}/channels`),
};

export default channelApi;
