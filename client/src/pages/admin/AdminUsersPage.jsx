import { useEffect, useState, useRef } from 'react';
import { Typography, Input, Table, Tag, Select, Button, Space, message, Modal, Form } from 'antd';
import { SearchOutlined, EditOutlined } from '@ant-design/icons';
import memberApi from '../../api/memberApi';

const { Title } = Typography;
const { Option } = Select;

const ROLE_COLOR = { ADMIN: 'red', USER: 'blue', MODERATOR: 'orange' };

export default function AdminUsersPage() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState(undefined);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [editingUser, setEditingUser] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchUsers();
  }, [pagination.current, pagination.pageSize, search, roleFilter]);

  async function fetchUsers() {
    setLoading(true);
    try {
      const res = await memberApi.getAllUsers({
        page: pagination.current - 1,
        size: pagination.pageSize,
        search: search || undefined,
        role: roleFilter || undefined,
      });
      const data = res.data;
      setUsers(data?.content || data || []);
      setTotal(data?.totalElements || (data?.length ?? 0));
    } catch {
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }

  const handleEdit = (user) => {
    setEditingUser(user);
    form.setFieldsValue({ role: user.role, status: user.status });
    setModalOpen(true);
  };

  const handleSave = async (values) => {
    setSaving(true);
    try {
      await memberApi.updateUser(editingUser.id, values);
      message.success('User updated');
      setModalOpen(false);
      fetchUsers();
    } catch {
      message.error('Failed to update user');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: 'Username',
      dataIndex: 'username',
      sorter: true,
    },
    {
      title: 'Display Name',
      dataIndex: 'displayName',
    },
    {
      title: 'Email',
      dataIndex: 'email',
    },
    {
      title: 'Role',
      dataIndex: 'role',
      filters: [
        { text: 'Admin', value: 'ADMIN' },
        { text: 'User', value: 'USER' },
        { text: 'Moderator', value: 'MODERATOR' },
      ],
      render: (role) => <Tag color={ROLE_COLOR[role] || 'default'}>{role}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (status) => (
        <Tag color={status === 'ACTIVE' ? 'success' : 'error'}>{status || 'ACTIVE'}</Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_, record) => (
        <Button
          size="small"
          icon={<EditOutlined />}
          onClick={() => handleEdit(record)}
        >
          Edit
        </Button>
      ),
    },
  ];

  const handleTableChange = (pag, filters, sorter) => {
    setPagination({ current: pag.current, pageSize: pag.pageSize });
  };

  return (
    <div>
      <Title level={3} style={{ marginBottom: 24 }}>
        User Management
      </Title>

      {/* Filters */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          prefix={<SearchOutlined />}
          placeholder="Search users..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPagination((p) => ({ ...p, current: 1 }));
          }}
          style={{ width: 220 }}
          allowClear
        />
        <Select
          placeholder="Filter by role"
          value={roleFilter}
          onChange={(v) => { setRoleFilter(v); setPagination((p) => ({ ...p, current: 1 })); }}
          allowClear
          style={{ width: 160 }}
        >
          <Option value="ADMIN">Admin</Option>
          <Option value="USER">User</Option>
          <Option value="MODERATOR">Moderator</Option>
        </Select>
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} users`,
        }}
        onChange={handleTableChange}
        bordered={false}
        size="middle"
      />

      <Modal
        title="Edit User"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText="Save"
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 16 }}>
          <Form.Item name="role" label="Role" rules={[{ required: true }]}>
            <Select>
              <Option value="ADMIN">Admin</Option>
              <Option value="USER">User</Option>
              <Option value="MODERATOR">Moderator</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="Status" rules={[{ required: true }]}>
            <Select>
              <Option value="ACTIVE">Active</Option>
              <Option value="INACTIVE">Inactive</Option>
              <Option value="BANNED">Banned</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
