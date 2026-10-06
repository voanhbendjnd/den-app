import { useEffect, useState } from 'react';
import { Typography, List, Avatar, Tag, Button, Badge, Empty, Skeleton, message } from 'antd';
import {
  BellOutlined, MessageOutlined, CalendarOutlined,
  TeamOutlined, CheckOutlined,
} from '@ant-design/icons';
import notificationApi from '../../api/notificationApi';
import { devNotifications } from '../../mocks/devData';
import PageError from '../../components/common/PageError';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

const { Title, Text } = Typography;

const TYPE_META = {
  MESSAGE: { color: '#1677ff', icon: <MessageOutlined /> },
  MEETING: { color: '#52c41a', icon: <CalendarOutlined /> },
  INVITE: { color: '#fa8c16', icon: <TeamOutlined /> },
};

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [markingAll, setMarkingAll] = useState(false);

  useEffect(() => {
    fetchNotifications();
  }, []);

  async function fetchNotifications() {
    setLoading(true);
    setError(null);
    try {
      const res = await notificationApi.getNotifications();
      setNotifications(res.data?.content || res.data || []);
    } catch {
      setNotifications(devNotifications);
    } finally {
      setLoading(false);
    }
  }

  async function handleMarkRead(id) {
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
    } catch {
      message.error('Failed to mark as read');
    }
  }

  async function handleMarkAllRead() {
    setMarkingAll(true);
    try {
      await notificationApi.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      message.success('All notifications marked as read');
    } catch {
      message.error('Failed to mark all as read');
    } finally {
      setMarkingAll(false);
    }
  }

  const unreadCount = notifications.filter((n) => !n.read).length;

  if (error) return <PageError message={error} onRetry={fetchNotifications} />;

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Title level={3} style={{ margin: 0 }}>
            Notifications
          </Title>
          {unreadCount > 0 && <Badge count={unreadCount} />}
        </div>
        {unreadCount > 0 && (
          <Button
            icon={<CheckOutlined />}
            onClick={handleMarkAllRead}
            loading={markingAll}
            size="small"
          >
            Mark all as read
          </Button>
        )}
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : notifications.length === 0 ? (
        <Empty description="You have no notifications." />
      ) : (
        <List
          dataSource={notifications}
          renderItem={(notif) => {
            const meta = TYPE_META[notif.type] || { color: '#8c8c8c', icon: <BellOutlined /> };
            return (
              <List.Item
                style={{
                  padding: '14px 16px',
                  borderRadius: 8,
                  marginBottom: 8,
                  backgroundColor: notif.read ? '#fff' : '#f0f7ff',
                  border: '1px solid',
                  borderColor: notif.read ? '#f0f0f0' : '#bae0ff',
                  cursor: notif.read ? 'default' : 'pointer',
                }}
                onClick={() => !notif.read && handleMarkRead(notif.id)}
                actions={[
                  !notif.read && (
                    <Button
                      type="text"
                      size="small"
                      key="read"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleMarkRead(notif.id);
                      }}
                    >
                      Mark read
                    </Button>
                  ),
                ].filter(Boolean)}
              >
                <List.Item.Meta
                  avatar={
                    <Avatar
                      icon={meta.icon}
                      style={{ backgroundColor: meta.color }}
                      size={36}
                    />
                  }
                  title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text strong={!notif.read} style={{ fontSize: 14 }}>
                        {notif.title}
                      </Text>
                      {!notif.read && <Badge dot />}
                      <Tag color={meta.color === '#1677ff' ? 'blue' : 'default'} style={{ margin: 0, fontSize: 11 }}>
                        {notif.type}
                      </Tag>
                    </div>
                  }
                  description={
                    <div>
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        {notif.body}
                      </Text>
                      <br />
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {dayjs(notif.createdAt).fromNow()}
                      </Text>
                    </div>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}
    </div>
  );
}
