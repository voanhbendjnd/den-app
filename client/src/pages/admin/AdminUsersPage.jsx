import { useEffect, useState } from 'react';
import {
  Typography,
  Input,
  Table,
  Tag,
  Select,
  Button,
  Space,
  Modal,
  Form,
  Avatar,
  Card,
  Alert,
  Spin,
  Switch,
  Tooltip,
  App,
} from 'antd';
import {
  SearchOutlined,
  EditOutlined,
  DeleteOutlined,
  PlusOutlined,
  UndoOutlined,
  UserOutlined,
  SafetyCertificateOutlined,
  ReloadOutlined,
  CrownOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import adminUserApi from '../../api/adminUserApi';
import { useAuth } from '../../hooks/useAuth';

const { Title, Text } = Typography;
const { Option } = Select;

export default function AdminUsersPage() {
  const { message } = App.useApp();
  const { currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState(undefined);
  const [statusFilter, setStatusFilter] = useState(undefined);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  // Create / Edit modal state
  const [formModalOpen, setFormModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  // Soft Delete & Ownership Preview modal state
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deletingUser, setDeletingUser] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [ownershipPreview, setOwnershipPreview] = useState(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    fetchUsers();
  }, [pagination.current, pagination.pageSize, search, roleFilter, statusFilter]);

  async function fetchUsers() {
    setLoading(true);
    try {
      const res = await adminUserApi.getAll({
        page: pagination.current - 1,
        size: pagination.pageSize,
        search: search.trim() || undefined,
        role: roleFilter || undefined,
        activated: statusFilter === undefined ? undefined : statusFilter === 'ACTIVE',
      });
      const payload = res.data?.data || res.data;
      setUsers(payload?.items || []);
      setTotal(payload?.totalElements || 0);
    } catch (err) {
      const detail = err?.response?.data?.detail || err?.response?.data?.message || 'Không thể tải danh sách tài khoản';
      message.error(detail);
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }

  const handleOpenCreate = () => {
    setEditingUser(null);
    form.resetFields();
    form.setFieldsValue({
      role: 'ROLE_USER',
      activated: true,
    });
    setFormModalOpen(true);
  };

  const handleOpenEdit = (record) => {
    setEditingUser(record);
    form.resetFields();
    form.setFieldsValue({
      email: record.email,
      name: record.name,
      username: record.username,
      role: record.role || 'ROLE_USER',
      activated: Boolean(record.activated),
      password: '',
    });
    setFormModalOpen(true);
  };

  const handleSaveUser = async (values) => {
    setSaving(true);
    try {
      if (editingUser) {
        const updatePayload = {
          email: values.email?.trim(),
          name: values.name?.trim(),
          username: values.username?.trim(),
          role: values.role,
          activated: values.activated,
        };
        if (values.password && values.password.trim()) {
          updatePayload.password = values.password.trim();
        }
        await adminUserApi.update(editingUser.id, updatePayload);
        message.success('Cập nhật tài khoản thành công');
      } else {
        await adminUserApi.create({
          email: values.email?.trim(),
          name: values.name?.trim(),
          username: values.username?.trim() || undefined,
          password: values.password,
          role: values.role,
          activated: values.activated,
        });
        message.success('Tạo tài khoản mới thành công');
      }
      setFormModalOpen(false);
      fetchUsers();
    } catch (err) {
      const detail = err?.response?.data?.detail || err?.response?.data?.message || 'Thao tác thất bại';
      message.error(detail);
    } finally {
      setSaving(false);
    }
  };

  const handleOpenSoftDelete = async (record) => {
    setDeletingUser(record);
    setOwnershipPreview(null);
    setDeleteModalOpen(true);
    setPreviewLoading(true);
    try {
      const res = await adminUserApi.getOwnershipPreview(record.id);
      const payload = res.data?.data || res.data;
      setOwnershipPreview(payload);
    } catch (err) {
      const detail = err?.response?.data?.detail || 'Không thể kiểm tra danh sách Server sở hữu';
      message.error(detail);
      setDeleteModalOpen(false);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleConfirmSoftDelete = async () => {
    if (!deletingUser) return;
    setDeleting(true);
    try {
      const res = await adminUserApi.softDelete(deletingUser.id);
      const result = res.data?.data || res.data;
      const transferred = result?.transferredServerCount || 0;
      const deletedEmpty = result?.deletedEmptyServerCount || 0;
      if (transferred > 0 || deletedEmpty > 0) {
        message.success(
          `Đã xóa mềm tài khoản. Tự động chuyển quyền ${transferred} Server và xóa ${deletedEmpty} Server trống.`
        );
      } else {
        message.success('Đã xóa mềm tài khoản thành công.');
      }
      setDeleteModalOpen(false);
      setDeletingUser(null);
      fetchUsers();
    } catch (err) {
      const detail = err?.response?.data?.detail || err?.response?.data?.message || 'Không thể xóa mềm tài khoản';
      message.error(detail);
    } finally {
      setDeleting(false);
    }
  };

  const handleRestoreUser = async (record) => {
    try {
      await adminUserApi.restore(record.id);
      message.success(`Đã khôi phục tài khoản ${record.email}`);
      fetchUsers();
    } catch (err) {
      const detail = err?.response?.data?.detail || err?.response?.data?.message || 'Không thể khôi phục tài khoản';
      message.error(detail);
    }
  };

  const isCurrentUser = (record) =>
    currentUser && (String(currentUser.id) === String(record.id) || currentUser.email === record.email);

  const columns = [
    {
      title: 'Tài khoản',
      key: 'user',
      render: (_, record) => (
        <Space size={12}>
          <Avatar
            size={38}
            src={record.avatar}
            icon={<UserOutlined />}
            style={{
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              fontWeight: 700,
              flexShrink: 0,
            }}
          >
            {!record.avatar && (record.name || record.username || 'U').charAt(0).toUpperCase()}
          </Avatar>
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.35 }}>
            <Space size={6}>
              <Text style={{ color: '#F1F5F9', fontWeight: 600 }}>
                {record.name || 'Chưa đặt tên'}
              </Text>
              {isCurrentUser(record) && (
                <Tag color="cyan" style={{ margin: 0, fontSize: 11 }}>Bạn</Tag>
              )}
            </Space>
            <Text style={{ color: '#94A3B8', fontSize: 12 }}>
              @{record.username || 'user'}
            </Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      render: (email) => <Text style={{ color: '#CBD5E1' }}>{email}</Text>,
    },
    {
      title: 'Vai trò',
      dataIndex: 'role',
      width: 130,
      render: (role) =>
        role === 'ROLE_ADMIN' ? (
          <Tag icon={<SafetyCertificateOutlined />} color="purple">
            ADMIN
          </Tag>
        ) : (
          <Tag color="blue">USER</Tag>
        ),
    },
    {
      title: 'Server làm chủ',
      dataIndex: 'ownedServerCount',
      width: 150,
      align: 'center',
      render: (count) =>
        count > 0 ? (
          <Tag icon={<CrownOutlined />} color="gold">
            {count} Server
          </Tag>
        ) : (
          <Text style={{ color: '#64748B', fontSize: 12 }}>0</Text>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'activated',
      width: 150,
      render: (activated) =>
        activated ? (
          <Tag color="success">Đang hoạt động</Tag>
        ) : (
          <Tag color="error">Đã xóa mềm (Khóa)</Tag>
        ),
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 210,
      render: (_, record) => {
        const self = isCurrentUser(record);
        return (
          <Space size={8}>
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleOpenEdit(record)}
            >
              Sửa
            </Button>
            {record.activated ? (
              <Tooltip title={self ? 'Không thể tự xóa mềm tài khoản của chính mình' : 'Xóa mềm & chuyển quyền chủ Server'}>
                <Button
                  size="small"
                  danger
                  disabled={self}
                  icon={<DeleteOutlined />}
                  onClick={() => handleOpenSoftDelete(record)}
                >
                  Xóa
                </Button>
              </Tooltip>
            ) : (
              <Button
                size="small"
                type="primary"
                ghost
                icon={<UndoOutlined />}
                onClick={() => handleRestoreUser(record)}
              >
                Khôi phục
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ padding: '28px 32px', maxWidth: 1280, margin: '0 auto' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 16,
          marginBottom: 24,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0, color: '#F1F5F9' }}>
            Quản trị Tài khoản
          </Title>
          <Text style={{ color: '#94A3B8', fontSize: 13 }}>
            Quản lý danh sách tài khoản hệ thống và tự động xử lý quyền sở hữu Server khi xóa mềm
          </Text>
        </div>

        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchUsers} loading={loading}>
            Làm mới
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenCreate}>
            Tạo tài khoản
          </Button>
        </Space>
      </div>

      <Card
        style={{
          background: 'var(--color-bg-surface, #151D2E)',
          border: '1px solid var(--border-subtle, rgba(255,255,255,0.08))',
          borderRadius: 14,
        }}
        styles={{ body: { padding: 20 } }}
      >
        <Space style={{ marginBottom: 18 }} wrap>
          <Input
            prefix={<SearchOutlined style={{ color: '#64748B' }} />}
            placeholder="Tìm theo email, username, tên..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPagination((p) => ({ ...p, current: 1 }));
            }}
            style={{ width: 280 }}
            allowClear
          />
          <Select
            placeholder="Lọc theo vai trò"
            value={roleFilter}
            onChange={(v) => {
              setRoleFilter(v);
              setPagination((p) => ({ ...p, current: 1 }));
            }}
            allowClear
            style={{ width: 170 }}
          >
            <Option value="ADMIN">Quản trị viên (Admin)</Option>
            <Option value="USER">Người dùng (User)</Option>
          </Select>
          <Select
            placeholder="Lọc theo trạng thái"
            value={statusFilter}
            onChange={(v) => {
              setStatusFilter(v);
              setPagination((p) => ({ ...p, current: 1 }));
            }}
            allowClear
            style={{ width: 190 }}
          >
            <Option value="ACTIVE">Đang hoạt động</Option>
            <Option value="INACTIVE">Đã xóa mềm (Khóa)</Option>
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
            showTotal: (t) => `Tổng ${t} tài khoản`,
          }}
          onChange={(pag) => setPagination({ current: pag.current, pageSize: pag.pageSize })}
          size="middle"
        />
      </Card>

      {/* Modal Tạo / Cập nhật tài khoản */}
      <Modal
        title={editingUser ? `Chỉnh sửa tài khoản #${editingUser.id}` : 'Tạo tài khoản mới'}
        open={formModalOpen}
        onCancel={() => setFormModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editingUser ? 'Lưu thay đổi' : 'Tạo tài khoản'}
        cancelText="Hủy"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={handleSaveUser} style={{ marginTop: 16 }}>
          <Form.Item
            name="email"
            label="Địa chỉ Email"
            rules={[
              { required: true, message: 'Vui lòng nhập email' },
              { type: 'email', message: 'Email không đúng định dạng' },
            ]}
          >
            <Input placeholder="user@denhub.com" />
          </Form.Item>

          <Form.Item
            name="name"
            label="Tên hiển thị"
            rules={[{ max: 50, message: 'Tên hiển thị tối đa 50 ký tự' }]}
          >
            <Input placeholder="Nguyễn Văn A" />
          </Form.Item>

          <Form.Item
            name="username"
            label="Username"
            rules={[{ max: 100, message: 'Username tối đa 100 ký tự' }]}
          >
            <Input placeholder="Để trống sẽ tự sinh từ email" />
          </Form.Item>

          <Form.Item
            name="password"
            label={editingUser ? 'Mật khẩu mới (để trống nếu giữ nguyên)' : 'Mật khẩu khởi tạo'}
            rules={
              editingUser
                ? [{ min: 6, max: 100, message: 'Mật khẩu phải từ 6 đến 100 ký tự' }]
                : [
                  { required: true, message: 'Vui lòng nhập mật khẩu' },
                  { min: 6, max: 100, message: 'Mật khẩu phải từ 6 đến 100 ký tự' },
                ]
            }
          >
            <Input.Password placeholder={editingUser ? 'Nhập nếu muốn đổi mật khẩu...' : 'Tối thiểu 6 ký tự'} />
          </Form.Item>

          <Form.Item name="role" label="Vai trò hệ thống" rules={[{ required: true }]}>
            <Select disabled={editingUser && isCurrentUser(editingUser)}>
              <Option value="ROLE_USER">Người dùng (ROLE_USER)</Option>
              <Option value="ROLE_ADMIN">Quản trị viên (ROLE_ADMIN)</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="activated"
            label="Trạng thái kích hoạt"
            valuePropName="checked"
            extra={
              editingUser && editingUser.activated && editingUser.ownedServerCount > 0
                ? 'Lưu ý: Tắt kích hoạt chủ Server sẽ tự động chuyển quyền chủ Server cho thành viên vào sớm nhất hoặc xóa Server trống.'
                : undefined
            }
          >
            <Switch
              disabled={editingUser && isCurrentUser(editingUser)}
              checkedChildren="Đang hoạt động"
              unCheckedChildren="Đã khóa (Xóa mềm)"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal Xác nhận Xóa mềm & Kiểm tra Chủ Server */}
      <Modal
        title={
          <Space>
            <ExclamationCircleOutlined style={{ color: '#EF4444' }} />
            <span>Xác nhận xóa mềm tài khoản</span>
          </Space>
        }
        open={deleteModalOpen}
        onCancel={() => {
          if (!deleting) {
            setDeleteModalOpen(false);
            setDeletingUser(null);
          }
        }}
        onOk={handleConfirmSoftDelete}
        confirmLoading={deleting}
        okButtonProps={{ danger: true, disabled: previewLoading }}
        okText="Xác nhận xóa mềm"
        cancelText="Hủy"
        width={580}
        destroyOnHidden
      >
        {previewLoading ? (
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <Spin />
            <div style={{ marginTop: 12, color: '#94A3B8' }}>
              Đang kiểm tra các Server mà tài khoản này đang làm chủ...
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginTop: 12 }}>
            <div>
              Bạn đang thực hiện xóa mềm (vô hiệu hóa) tài khoản{' '}
              <Text strong style={{ color: '#F1F5F9' }}>
                {deletingUser?.name || deletingUser?.username} ({deletingUser?.email})
              </Text>
              . Tài khoản sẽ bị khóa và đăng xuất khỏi toàn bộ thiết bị ngay lập tức.
            </div>

            {ownershipPreview && ownershipPreview.totalOwnedServers > 0 ? (
              <Alert
                type="warning"
                showIcon
                message={`Tài khoản này đang là Chủ sở hữu (Owner) của ${ownershipPreview.totalOwnedServers} Server`}
                description={
                  <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div>
                      Hệ thống sẽ tự động xử lý quyền sở hữu theo đúng quy tắc:
                    </div>
                    <div
                      style={{
                        maxHeight: 220,
                        overflowY: 'auto',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 6,
                        paddingRight: 4,
                      }}
                    >
                      {ownershipPreview.servers.map((srv) => (
                        <div
                          key={srv.serverId}
                          style={{
                            padding: '8px 12px',
                            borderRadius: 8,
                            background: 'rgba(15, 20, 34, 0.65)',
                            border: '1px solid rgba(255,255,255,0.08)',
                          }}
                        >
                          <div style={{ fontWeight: 600, color: '#F1F5F9' }}>
                            Server: {srv.serverName} (#{srv.serverId})
                          </div>
                          {srv.action === 'TRANSFER_OWNERSHIP' ? (
                            <div style={{ fontSize: 12, color: '#10B981', marginTop: 2 }}>
                              → Tự động chuyển quyền Chủ Server cho{' '}
                              <strong>
                                {srv.nextOwnerName || srv.nextOwnerUsername} (@{srv.nextOwnerUsername})
                              </strong>{' '}
                              (thành viên gia nhập sớm nhất đang hoạt động)
                            </div>
                          ) : (
                            <div style={{ fontSize: 12, color: '#F87171', marginTop: 2 }}>
                              → Server chỉ có duy nhất chủ sở hữu (không có thành viên active khác):{' '}
                              <strong>Tự động giải tán (xóa) Server trống này</strong>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                }
              />
            ) : (
              <Alert
                type="info"
                showIcon
                message="Tài khoản này hiện không làm chủ Server nào."
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
