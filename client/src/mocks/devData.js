/**
 * DEV-ONLY temporary sample data.
 * Used only when the backend is not available yet.
 * Remove or disable this file once the backend is running.
 */

export const devRooms = [
  {
    id: '1',
    name: 'General',
    slug: 'general',
    description: 'General discussion for the whole team.',
    memberCount: 12,
    unreadCount: 3,
    lastActivity: new Date().toISOString(),
    visibility: 'PUBLIC',
  },
  {
    id: '2',
    name: 'Backend Team 01',
    slug: 'backend-team-01',
    description: 'Spring Boot development discussions.',
    memberCount: 5,
    unreadCount: 0,
    lastActivity: new Date(Date.now() - 3600000).toISOString(),
    visibility: 'PRIVATE',
  },
  {
    id: '3',
    name: 'Frontend UI',
    slug: 'frontend-ui',
    description: 'React + Ant Design discussions.',
    memberCount: 4,
    unreadCount: 7,
    lastActivity: new Date(Date.now() - 7200000).toISOString(),
    visibility: 'PRIVATE',
  },
];

export const devMeetings = [
  {
    id: '1',
    title: 'Sprint Planning',
    roomName: 'General',
    startTime: new Date(Date.now() + 86400000).toISOString(),
    endTime: new Date(Date.now() + 90000000).toISOString(),
    host: 'admin',
    status: 'UPCOMING',
    participantCount: 8,
  },
  {
    id: '2',
    title: 'Backend Review',
    roomName: 'Backend Team 01',
    startTime: new Date(Date.now() - 86400000).toISOString(),
    endTime: new Date(Date.now() - 83000000).toISOString(),
    host: 'dev1',
    status: 'COMPLETED',
    participantCount: 4,
  },
];

export const devNotifications = [
  {
    id: '1',
    type: 'MESSAGE',
    title: 'New message in General',
    body: 'admin: Welcome everyone!',
    read: false,
    createdAt: new Date(Date.now() - 1800000).toISOString(),
  },
  {
    id: '2',
    type: 'MEETING',
    title: 'Sprint Planning reminder',
    body: 'Meeting starts in 1 hour.',
    read: false,
    createdAt: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: '3',
    type: 'INVITE',
    title: 'Room invitation',
    body: 'You were invited to Frontend UI.',
    read: true,
    createdAt: new Date(Date.now() - 86400000).toISOString(),
  },
];
