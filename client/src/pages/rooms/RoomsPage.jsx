import { useEffect, useState } from 'react';
import {
  Row, Col, Card, Typography, Input, Button, Modal, Form,
  Select, Tag, Avatar, Badge, Tooltip, message,
} from 'antd';
import { PlusOutlined, SearchOutlined, TeamOutlined, LockOutlined, GlobalOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import roomApi from '../../api/roomApi';
import { devRooms } from '../../mocks/devData';
import { toSlug } from '../../utils/slug';
import PageLoading from '../../components/common/PageLoading';
import PageError from '../../components/common/PageError';
import EmptyState from '../../components/common/EmptyState';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

const { Title, Text } = Typography;
const { Option } = Select;

export default function RoomsPage() {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [filterVisibility, setFilterVisibility] = useState('ALL');
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();
  const nameValue = Form.useWatch('name', form);

  useEffect(() => {
    fetchRooms();
  }, []);

  async function fetchRooms() {
    setLoading(true);
    setError(null);
    try {
      const res = await roomApi.getRooms();
      setRooms(res.data?.content || res.data || []);
    } catch {
      setRooms(devRooms); // fallback to dev data
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateRoom(values) {
    setCreating(true);
    try {
      const payload = { ...values, slug: toSlug(values.name) };
      await roomApi.createRoom(payload);
      message.success('Room created successfully');
      setModalOpen(false);
      form.resetFields();
      fetchRooms();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to create room';
      message.error(msg);
    } finally {
      setCreating(false);
    }
  }

  const filteredRooms = rooms.filter((room) => {
    const matchSearch = room.name?.toLowerCase().includes(search.toLowerCase());
    const matchVisibility = filterVisibility === 'ALL' || room.visibility === filterVisibility;
    return matchSearch && matchVisibility;
  });

  if (loading) return <PageLoading />;
  if (error) return <PageError message={error} onRetry={fetchRooms} />;

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Title level={3} style={{ margin: 0 }}>
          Rooms
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          New Room
        </Button>
      </div>

      {/* Filters */}
      <Row gutter={12} style={{ marginBottom: 20 }}>
        <Col flex="1">
          <Input
            prefix={<SearchOutlined />}
            placeholder="Search rooms..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            allowClear
          />
        </Col>
        <Col>
          <Select
            value={filterVisibility}
            onChange={setFilterVisibility}
            style={{ width: 130 }}
          >
            <Option value="ALL">All</Option>
            <Option value="PUBLIC">Public</Option>
            <Option value="PRIVATE">Private</Option>
          </Select>
        </Col>
      </Row>

      {/* Room grid */}
      {filteredRooms.length === 0 ? (
        <EmptyState description="No rooms found. Create one to get started." />
      ) : (
        <Row gutter={[16, 16]}>
          {filteredRooms.map((room) => (
            <Col key={room.id} xs={24} sm={12} lg={8}>
              <Card
                hoverable
                style={{ borderRadius: 8, cursor: 'pointer' }}
                onClick={() => navigate(`/rooms/${room.slug}`)}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                  <Avatar
                    size={44}
                    style={{ backgroundColor: '#1677ff', flexShrink: 0 }}
                  >
                    {room.name?.[0]?.toUpperCase()}
                  </Avatar>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text strong style={{ fontSize: 15 }} ellipsis>
                        {room.name}
                      </Text>
                      {room.unreadCount > 0 && <Badge count={room.unreadCount} size="small" />}
                    </div>
                    <Text type="secondary" style={{ fontSize: 12, display: 'block' }} ellipsis>
                      #{room.slug}
                    </Text>
                    {room.description && (
                      <Text
                        type="secondary"
                        style={{ fontSize: 13, display: 'block', marginTop: 4 }}
                        ellipsis
                      >
                        {room.description}
                      </Text>
                    )}
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginTop: 10,
                      }}
                    >
                      <span>
                        <TeamOutlined style={{ marginRight: 4, color: '#8c8c8c' }} />
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {room.memberCount}
                        </Text>
                      </span>
                      <Tag
                        icon={room.visibility === 'PRIVATE' ? <LockOutlined /> : <GlobalOutlined />}
                        color={room.visibility === 'PRIVATE' ? 'default' : 'blue'}
                        style={{ margin: 0 }}
                      >
                        {room.visibility === 'PRIVATE' ? 'Private' : 'Public'}
                      </Tag>
                    </div>
                  </div>
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {/* Create Room Modal */}
      <Modal
        title="Create New Room"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        confirmLoading={creating}
        okText="Create"
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleCreateRoom} style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Room Name"
            rules={[{ required: true, message: 'Please enter a room name' }]}
          >
            <Input placeholder="e.g. Backend Team 01" maxLength={60} />
          </Form.Item>

          {nameValue && (
            <div style={{ marginTop: -8, marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Slug: <code>{toSlug(nameValue)}</code>
              </Text>
            </div>
          )}

          <Form.Item name="description" label="Description">
            <Input.TextArea placeholder="What is this room for?" rows={3} maxLength={200} />
          </Form.Item>

          <Form.Item
            name="visibility"
            label="Visibility"
            initialValue="PUBLIC"
            rules={[{ required: true }]}
          >
            <Select>
              <Option value="PUBLIC">Public — anyone can join</Option>
              <Option value="PRIVATE">Private — invite only</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
