import { useEffect, useState } from 'react';
import {
  Typography, Button, Modal, Form, Input, DatePicker,
  Select, List, Avatar, Tag, Tabs, message, Popconfirm, Skeleton,
} from 'antd';
import {
  PlusOutlined, CalendarOutlined, ClockCircleOutlined,
  UserOutlined, DeleteOutlined,
} from '@ant-design/icons';
import meetingApi from '../../api/meetingApi';
import { devMeetings } from '../../mocks/devData';
import EmptyState from '../../components/common/EmptyState';
import PageError from '../../components/common/PageError';
import dayjs from 'dayjs';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;
const { Option } = Select;

const STATUS_COLOR = {
  UPCOMING: 'blue',
  IN_PROGRESS: 'green',
  COMPLETED: 'default',
  CANCELLED: 'red',
};

export default function MeetingsPage() {
  const [meetings, setMeetings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [activeTab, setActiveTab] = useState('upcoming');
  const [form] = Form.useForm();

  useEffect(() => {
    fetchMeetings();
  }, []);

  async function fetchMeetings() {
    setLoading(true);
    setError(null);
    try {
      const res = await meetingApi.getMeetings();
      setMeetings(res.data?.content || res.data || []);
    } catch {
      setMeetings(devMeetings);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(values) {
    setCreating(true);
    try {
      const [startTime, endTime] = values.timeRange;
      const payload = {
        title: values.title,
        description: values.description,
        startTime: startTime.toISOString(),
        endTime: endTime.toISOString(),
      };
      await meetingApi.createMeeting(payload);
      message.success('Meeting scheduled');
      setModalOpen(false);
      form.resetFields();
      fetchMeetings();
    } catch (err) {
      message.error(err.response?.data?.message || 'Failed to create meeting');
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(id) {
    try {
      await meetingApi.deleteMeeting(id);
      message.success('Meeting deleted');
      fetchMeetings();
    } catch {
      message.error('Failed to delete meeting');
    }
  }

  const upcoming = meetings.filter((m) => m.status === 'UPCOMING' || m.status === 'IN_PROGRESS');
  const past = meetings.filter((m) => m.status === 'COMPLETED' || m.status === 'CANCELLED');

  const renderMeetingList = (list) => {
    if (loading) return <Skeleton active paragraph={{ rows: 4 }} />;
    if (error) return <PageError message={error} onRetry={fetchMeetings} />;
    if (list.length === 0) return <EmptyState description="No meetings here." />;

    return (
      <List
        dataSource={list}
        renderItem={(meeting) => (
          <List.Item
            style={{ padding: '14px 0' }}
            actions={[
              <Popconfirm
                key="delete"
                title="Delete this meeting?"
                onConfirm={() => handleDelete(meeting.id)}
                okText="Yes"
                cancelText="No"
              >
                <Button type="text" danger icon={<DeleteOutlined />} size="small" />
              </Popconfirm>,
            ]}
          >
            <List.Item.Meta
              avatar={
                <Avatar
                  size={44}
                  icon={<CalendarOutlined />}
                  style={{ backgroundColor: STATUS_COLOR[meeting.status] === 'blue' ? '#1677ff' : '#8c8c8c' }}
                />
              }
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Text strong>{meeting.title}</Text>
                  <Tag color={STATUS_COLOR[meeting.status]}>{meeting.status}</Tag>
                </div>
              }
              description={
                <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                  <span>
                    <ClockCircleOutlined style={{ marginRight: 4 }} />
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {dayjs(meeting.startTime).format('ddd, MMM D · HH:mm')}
                      {' — '}
                      {dayjs(meeting.endTime).format('HH:mm')}
                    </Text>
                  </span>
                  {meeting.host && (
                    <span>
                      <UserOutlined style={{ marginRight: 4 }} />
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {meeting.host}
                      </Text>
                    </span>
                  )}
                  {meeting.roomName && (
                    <Tag style={{ margin: 0 }}>{meeting.roomName}</Tag>
                  )}
                </div>
              }
            />
          </List.Item>
        )}
      />
    );
  };

  return (
    <div style={{ maxWidth: 860, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>
          Meetings
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          Schedule Meeting
        </Button>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'upcoming', label: `Upcoming (${upcoming.length})`, children: renderMeetingList(upcoming) },
          { key: 'past', label: `Past (${past.length})`, children: renderMeetingList(past) },
        ]}
      />

      {/* Create Meeting Modal */}
      <Modal
        title="Schedule a Meeting"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        onOk={() => form.submit()}
        confirmLoading={creating}
        okText="Schedule"
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleCreate} style={{ marginTop: 16 }}>
          <Form.Item name="title" label="Title" rules={[{ required: true }]}>
            <Input placeholder="Meeting title" />
          </Form.Item>
          <Form.Item
            name="timeRange"
            label="Start & End Time"
            rules={[{ required: true, message: 'Please select start and end time' }]}
          >
            <RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Optional description" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
