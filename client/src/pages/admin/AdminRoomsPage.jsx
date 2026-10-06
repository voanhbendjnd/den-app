import { useEffect, useState } from 'react';
import {
  Typography, Input, Table, Tag, Button, Space, message, Popconfirm, Modal, Form, Select,
} from 'antd';
import { SearchOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import roomApi from '../../api/roomApi';

const { Title } = Typography;
const { Option } = Select;

export default function AdminRoomsPage() {
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [editingRoom, setEditingRoom] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchRooms();
  }, [pagination.current, pagination.pageSize, search]);

  async function fetchRooms() {
    setLoading(true);
    try {
      const res = await roomApi.getAllRooms({
        page: pagination.current - 1,
        size: pagination.pageSize,
        search: search || undefined,
      });
      const data = res.data;
      setRooms(data?.content || data || []);
      setTotal(data?.totalElements || (data?.length ?? 0));
    } catch {
      setRooms([]);
    } finally {
      setLoading(false);
    }
  }

  const handleEdit = (room) => {
    setEditingRoom(room);
    form.setFieldsValue({ name: room.name, description: room.description, visibility: room.visibility });
    setModalOpen(true);
  };

  const handleSave = async (values) => {
    setSaving(true);
    try {
      await roomApi.updateRoom(editingRoom.id, values);
      message.success('Room updated');
      setModalOpen(false);
      fetchRooms();
    } catch {
      message.error('Failed to update room');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      await roomApi.deleteRoom(id);
      message.success('Room deleted');
      fetchRooms();
    } catch {
      message.error('Failed to delete room');
    }
  };

  const columns = [
    { title: 'Name', dataIndex: 'name', sorter: true },
    { title: 'Slug', dataIndex: 'slug', render: (slug) => <code>#{slug}</code> },
    {
      title: 'Visibility',
      dataIndex: 'visibility',
      filters: [
        { text: 'Public', value: 'PUBLIC' },
        { text: 'Private', value: 'PRIVATE' },
      ],
      render: (v) => <Tag color={v === 'PUBLIC' ? 'blue' : 'default'}>{v}</Tag>,
    },
    { title: 'Members', dataIndex: 'memberCount', sorter: true },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            Edit
          </Button>
          <Popconfirm
            title="Delete this room?"
            description="This action cannot be undone."
            onConfirm={() => handleDelete(record.id)}
            okText="Delete"
            okButtonProps={{ danger: true }}
            cancelText="Cancel"
          >
            <Button size="small" danger icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={3} style={{ marginBottom: 24 }}>
        Room Management
      </Title>

      <Space style={{ marginBottom: 16 }}>
        <Input
          prefix={<SearchOutlined />}
          placeholder="Search rooms..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPagination((p) => ({ ...p, current: 1 }));
          }}
          style={{ width: 220 }}
          allowClear
        />
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={rooms}
        loading={loading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} rooms`,
        }}
        onChange={(pag) => setPagination({ current: pag.current, pageSize: pag.pageSize })}
        size="middle"
      />

      <Modal
        title="Edit Room"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText="Save"
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 16 }}>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="visibility" label="Visibility" rules={[{ required: true }]}>
            <Select>
              <Option value="PUBLIC">Public</Option>
              <Option value="PRIVATE">Private</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
