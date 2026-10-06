import { useState } from 'react';
import {
  Card, Avatar, Typography, Form, Input, Button, Tag, Divider,
  Row, Col, message,
} from 'antd';
import { UserOutlined, EditOutlined, SaveOutlined } from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import authApi from '../../api/authApi';

const { Title, Text } = Typography;

const ROLE_COLOR = { ADMIN: 'red', USER: 'blue', MODERATOR: 'orange' };

export default function ProfilePage() {
  const { currentUser, role } = useAuth();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const handleEdit = () => {
    form.setFieldsValue({
      displayName: currentUser?.displayName,
      email: currentUser?.email,
    });
    setEditing(true);
  };

  const handleSave = async (values) => {
    setSaving(true);
    try {
      // PUT /users/me or similar — confirm endpoint with backend
      await authApi.getMe(); // placeholder; replace with updateMe(values)
      message.success('Profile updated');
      setEditing(false);
    } catch {
      message.error('Failed to update profile');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <Title level={3} style={{ marginBottom: 24 }}>
        Profile
      </Title>

      <Card style={{ borderRadius: 8 }}>
        {/* Avatar + name */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 20, marginBottom: 28 }}>
          <Avatar
            size={72}
            icon={<UserOutlined />}
            style={{ backgroundColor: '#1677ff' }}
          />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {currentUser?.displayName || currentUser?.username}
            </Title>
            <Text type="secondary">@{currentUser?.username}</Text>
            <div style={{ marginTop: 6 }}>
              <Tag color={ROLE_COLOR[role] || 'default'}>{role || 'USER'}</Tag>
            </div>
          </div>
        </div>

        <Divider />

        {editing ? (
          <Form form={form} layout="vertical" onFinish={handleSave}>
            <Row gutter={16}>
              <Col xs={24} sm={12}>
                <Form.Item name="displayName" label="Display Name" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item name="email" label="Email" rules={[{ type: 'email' }]}>
                  <Input />
                </Form.Item>
              </Col>
            </Row>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
                Save
              </Button>
              <Button onClick={() => setEditing(false)}>Cancel</Button>
            </div>
          </Form>
        ) : (
          <>
            <Row gutter={[16, 12]}>
              <Col xs={24} sm={12}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                  Display Name
                </Text>
                <Text>{currentUser?.displayName || '—'}</Text>
              </Col>
              <Col xs={24} sm={12}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                  Username
                </Text>
                <Text>@{currentUser?.username || '—'}</Text>
              </Col>
              <Col xs={24} sm={12}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                  Email
                </Text>
                <Text>{currentUser?.email || '—'}</Text>
              </Col>
              <Col xs={24} sm={12}>
                <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                  Role
                </Text>
                <Tag color={ROLE_COLOR[role] || 'default'}>{role || 'USER'}</Tag>
              </Col>
            </Row>

            <Button
              type="default"
              icon={<EditOutlined />}
              onClick={handleEdit}
              style={{ marginTop: 24 }}
            >
              Edit Profile
            </Button>
          </>
        )}
      </Card>
    </div>
  );
}
