import { useState } from 'react';
import { Form, Input, Button, Typography, Alert, Divider } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, ThunderboltFilled, ArrowRightOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import authApi from '../../api/authApi';

const { Title, Text } = Typography;

export default function RegisterPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (values) => {
    setLoading(true);
    setError(null);
    try {
      await authApi.register(values);
      navigate('/login');
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        'Đăng ký tài khoản không thành công. Vui lòng kiểm tra lại.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      width: '100%',
      maxWidth: 460,
      margin: '24px auto',
      padding: '0 16px',
      zIndex: 10,
    }}>
      {/* Brand Header */}
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <div style={{
          width: 52,
          height: 52,
          margin: '0 auto 10px',
          background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
          borderRadius: 16,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 6px 20px rgba(0, 242, 254, 0.3)',
        }}>
          <ThunderboltFilled style={{ fontSize: 26, color: '#fff' }} />
        </div>
        <Title level={2} style={{ margin: 0, color: '#fff', fontWeight: 800 }}>
          Den<span style={{ color: '#00F2FE' }}>Hub</span>
        </Title>
        <Text style={{ color: '#94A3B8', fontSize: 13 }}>
          Tạo tài khoản không gian số của bạn
        </Text>
      </div>

      <div 
        className="glass-panel"
        style={{
          padding: '32px 28px',
          borderRadius: 20,
          boxShadow: '0 16px 40px rgba(0, 0, 0, 0.6), inset 0 1px 0 rgba(255, 255, 255, 0.1)',
        }}
      >
        {error && (
          <Alert
            message={error}
            type="error"
            showIcon
            style={{ marginBottom: 20, borderRadius: 10, background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.2)' }}
            closable
            onClose={() => setError(null)}
          />
        )}

        <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
          <Form.Item
            name="displayName"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Tên hiển thị</Text>}
            rules={[{ required: true, message: 'Vui lòng nhập tên hiển thị' }]}
          >
            <Input prefix={<UserOutlined style={{ color: '#64748B' }} />} placeholder="Ví dụ: Nguyễn Văn A" style={{ height: 42 }} />
          </Form.Item>

          <Form.Item
            name="username"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Tên đăng nhập</Text>}
            rules={[
              { required: true, message: 'Vui lòng nhập tên đăng nhập' },
              { min: 3, message: 'Tối thiểu 3 ký tự' },
            ]}
          >
            <Input prefix={<UserOutlined style={{ color: '#64748B' }} />} placeholder="username" autoComplete="username" style={{ height: 42 }} />
          </Form.Item>

          <Form.Item
            name="email"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Email</Text>}
            rules={[
              { required: true, message: 'Vui lòng nhập email' },
              { type: 'email', message: 'Email không đúng định dạng' },
            ]}
          >
            <Input prefix={<MailOutlined style={{ color: '#64748B' }} />} placeholder="name@example.com" style={{ height: 42 }} />
          </Form.Item>

          <Form.Item
            name="password"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Mật khẩu</Text>}
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu' },
              { min: 6, message: 'Tối thiểu 6 ký tự' },
            ]}
          >
            <Input.Password prefix={<LockOutlined style={{ color: '#64748B' }} />} placeholder="Mật khẩu" style={{ height: 42 }} />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Xác nhận mật khẩu</Text>}
            dependencies={['password']}
            rules={[
              { required: true, message: 'Vui lòng xác nhận lại mật khẩu' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Mật khẩu xác nhận không khớp'));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined style={{ color: '#64748B' }} />} placeholder="Nhập lại mật khẩu" style={{ height: 42 }} />
          </Form.Item>

          <Form.Item style={{ marginTop: 24, marginBottom: 12 }}>
            <Button 
              type="primary" 
              htmlType="submit" 
              loading={loading} 
              block 
              style={{ 
                height: 46, 
                fontSize: 15, 
                fontWeight: 700,
                background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 50%, #00F2FE 100%)',
                border: 'none',
                boxShadow: '0 6px 20px rgba(99, 102, 241, 0.4)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
              }}
            >
              <span>Đăng Ký Tài Khoản</span>
              <ArrowRightOutlined />
            </Button>
          </Form.Item>
        </Form>

        <Divider style={{ borderColor: 'rgba(255, 255, 255, 0.08)', margin: '16px 0' }} />

        <div style={{ textAlign: 'center' }}>
          <Text style={{ color: '#94A3B8', fontSize: 13 }}>
            Đã có tài khoản? <Link to="/login" style={{ color: '#00F2FE', fontWeight: 600 }}>Đăng nhập ngay</Link>
          </Text>
        </div>
      </div>
    </div>
  );
}
