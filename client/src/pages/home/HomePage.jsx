import { useEffect, useState } from 'react';
import { Row, Col, Card, Typography, List, Badge, Avatar, Tag, Skeleton } from 'antd';
import {
  TeamOutlined,
  CalendarOutlined,
  BellOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import roomApi from '../../api/roomApi';
import meetingApi from '../../api/meetingApi';
import notificationApi from '../../api/notificationApi';
import { devRooms, devMeetings, devNotifications } from '../../mocks/devData';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

const { Title, Text } = Typography;

function StatCard({ icon, label, value, color }) {
  return (
    <Card size="small" style={{ borderRadius: 8 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 8,
            backgroundColor: `${color}18`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 20,
            color,
          }}
        >
          {icon}
        </div>
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {label}
          </Text>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {value}
            </Title>
          </div>
        </div>
      </div>
    </Card>
  );
}

export default function HomePage() {
  const { currentUser } = useAuth();
  const [rooms, setRooms] = useState([]);
  const [meetings, setMeetings] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchData() {
      try {
        const [roomRes, meetingRes, notifRes] = await Promise.allSettled([
          roomApi.getRooms({ limit: 5 }),
          meetingApi.getMeetings({ limit: 3 }),
          notificationApi.getNotifications({ limit: 5 }),
        ]);

        setRooms(roomRes.status === 'fulfilled' ? roomRes.value.data?.content || roomRes.value.data || [] : devRooms);
        setMeetings(meetingRes.status === 'fulfilled' ? meetingRes.value.data?.content || meetingRes.value.data || [] : devMeetings);
        setNotifications(notifRes.status === 'fulfilled' ? notifRes.value.data?.content || notifRes.value.data || [] : devNotifications);
      } catch {
        setRooms(devRooms);
        setMeetings(devMeetings);
        setNotifications(devNotifications);
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, []);

  const unreadCount = notifications.filter((n) => !n.read).length;
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      {/* Greeting */}
      <div style={{ marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>
          {greeting}, {currentUser?.displayName || currentUser?.username} 👋
        </Title>
        <Text type="secondary">Here&apos;s what&apos;s happening in your workspace.</Text>
      </div>

      {/* Summary row */}
      <Row gutter={[16, 16]} style={{ marginBottom: 28 }}>
        <Col xs={24} sm={8}>
          <StatCard icon={<TeamOutlined />} label="Rooms" value={rooms.length} color="#1677ff" />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard icon={<CalendarOutlined />} label="Upcoming Meetings" value={meetings.filter((m) => m.status === 'UPCOMING').length} color="#52c41a" />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard icon={<BellOutlined />} label="Unread Notifications" value={unreadCount} color="#fa8c16" />
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* Recent Rooms */}
        <Col xs={24} lg={14}>
          <Card
            title={
              <span>
                <TeamOutlined style={{ marginRight: 8 }} />
                Recent Rooms
              </span>
            }
            extra={<Link to="/rooms">View all</Link>}
            style={{ borderRadius: 8 }}
          >
            {loading ? (
              <Skeleton active paragraph={{ rows: 3 }} />
            ) : (
              <List
                dataSource={rooms.slice(0, 5)}
                renderItem={(room) => (
                  <List.Item
                    style={{ padding: '10px 0', cursor: 'pointer' }}
                    onClick={() => {}}
                  >
                    <List.Item.Meta
                      avatar={
                        <Avatar
                          style={{ backgroundColor: '#1677ff' }}
                          size={36}
                        >
                          {room.name?.[0]?.toUpperCase()}
                        </Avatar>
                      }
                      title={
                        <Link to={`/rooms/${room.slug}`}>
                          <Text strong>{room.name}</Text>
                        </Link>
                      }
                      description={
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {room.memberCount} members ·{' '}
                          {dayjs(room.lastActivity).fromNow()}
                        </Text>
                      }
                    />
                    {room.unreadCount > 0 && (
                      <Badge count={room.unreadCount} size="small" />
                    )}
                  </List.Item>
                )}
                locale={{ emptyText: 'No rooms yet.' }}
              />
            )}
          </Card>
        </Col>

        {/* Upcoming Meetings + Notifications */}
        <Col xs={24} lg={10}>
          <Card
            title={
              <span>
                <CalendarOutlined style={{ marginRight: 8 }} />
                Upcoming Meetings
              </span>
            }
            extra={<Link to="/meetings">View all</Link>}
            style={{ borderRadius: 8, marginBottom: 16 }}
          >
            {loading ? (
              <Skeleton active paragraph={{ rows: 2 }} />
            ) : (
              <List
                dataSource={meetings.filter((m) => m.status === 'UPCOMING').slice(0, 3)}
                renderItem={(meeting) => (
                  <List.Item style={{ padding: '8px 0' }}>
                    <List.Item.Meta
                      avatar={<Avatar icon={<CalendarOutlined />} size={32} style={{ backgroundColor: '#52c41a' }} />}
                      title={<Text strong style={{ fontSize: 13 }}>{meeting.title}</Text>}
                      description={
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {dayjs(meeting.startTime).format('ddd, MMM D · HH:mm')}
                        </Text>
                      }
                    />
                  </List.Item>
                )}
                locale={{ emptyText: 'No upcoming meetings.' }}
              />
            )}
          </Card>

          <Card
            title={
              <span>
                <BellOutlined style={{ marginRight: 8 }} />
                Notifications
              </span>
            }
            extra={<Link to="/notifications">View all</Link>}
            style={{ borderRadius: 8 }}
          >
            {loading ? (
              <Skeleton active paragraph={{ rows: 2 }} />
            ) : (
              <List
                dataSource={notifications.filter((n) => !n.read).slice(0, 3)}
                renderItem={(notif) => (
                  <List.Item style={{ padding: '8px 0' }}>
                    <List.Item.Meta
                      avatar={<Avatar icon={<MessageOutlined />} size={32} style={{ backgroundColor: '#fa8c16' }} />}
                      title={<Text style={{ fontSize: 13 }}>{notif.title}</Text>}
                      description={
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {notif.body}
                        </Text>
                      }
                    />
                  </List.Item>
                )}
                locale={{ emptyText: 'No unread notifications.' }}
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
