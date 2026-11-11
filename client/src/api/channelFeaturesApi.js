import api from './axiosClient';
const data = promise => promise.then(response => response.data?.data ?? response.data);
export const channelFeaturesApi = {
  search: (channel, q, before) => data(api.get(`/channels/${channel}/messages/search`, { params: { q, before, limit: 30 } })),
  pins: (channel, before) => data(api.get(`/channels/${channel}/pins`, { params: { before, limit: 30 } })),
  saved: before => data(api.get('/messages/saved', { params: { before, limit: 30 } })),
  pin: (id, enabled) => data(api[enabled ? 'put' : 'delete'](`/messages/${id}/pin`)),
  save: (id, enabled) => data(api[enabled ? 'put' : 'delete'](`/messages/${id}/saved`)),
  preference: channel => data(api.get(`/channels/${channel}/notification-preference`)),
  setPreference: (channel, muted) => data(api.put(`/channels/${channel}/notification-preference`, { muted })),
};
