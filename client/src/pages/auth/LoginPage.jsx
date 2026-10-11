import { useState } from 'react';
import { Form, Input, Button, Typography, Alert, Divider } from 'antd';
import { 
  UserOutlined, 
  LockOutlined, 
  ThunderboltFilled, 
  ArrowRightOutlined
} from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

const { Title, Text } = Typography;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (values) => {
    setLoading(true);
    setError(null);
    try {
      await login(values);
      navigate('/');
    } catch (err) {
      const status = err.response?.status;
      let msg = 'Đăng nhập không thành công. Vui lòng kiểm tra lại thông tin!';
      
      if (status === 401 || err.response?.data?.detail?.includes('Bad credentials') || err.response?.data?.title?.includes('Bad credentials')) {
        msg = 'Email hoặc mật khẩu không chính xác. Vui lòng thử lại!';
      } else if (err.response?.data?.message) {
        msg = err.response.data.message;
      } else if (err.response?.data?.error) {
        msg = err.response.data.error;
      }
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const fillDemoAccount = (email, password) => {
    form.setFieldsValue({ username: email, password: password });
    setError(null);
  };

  return (
    <div style={{
      width: '100%',
      maxWidth: 440,
      margin: '24px auto',
      padding: '0 16px',
      zIndex: 10,
    }}>
      {/* Brand Header */}
      <div style={{ textAlign: 'center', marginBottom: 28 }}>
        <div style={{
          width: 56,
          height: 56,
          margin: '0 auto 12px',
          background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
          borderRadius: 16,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 8px 24px rgba(0, 242, 254, 0.35)',
        }}>
          <ThunderboltFilled style={{ fontSize: 28, color: '#fff' }} />
        </div>
        
        <Title level={2} style={{ margin: 0, color: '#fff', fontWeight: 800, letterSpacing: '-0.5px' }}>
          Den<span style={{ color: '#00F2FE' }}>Hub</span>
        </Title>
        <Text style={{ color: '#94A3B8', fontSize: 14 }}>
          Không gian làm việc & giao tiếp nhóm
        </Text>
      </div>

      {/* Login Glass Card */}
      <div 
        className="glass-panel"
        style={{
          padding: '32px 28px',
          borderRadius: 18,
          boxShadow: '0 16px 40px rgba(0, 0, 0, 0.6), inset 0 1px 0 rgba(255, 255, 255, 0.08)',
        }}
      >
        <div style={{ marginBottom: 20 }}>
          <Title level={4} style={{ margin: 0, color: '#F1F5F9', fontWeight: 700 }}>
            Đăng nhập
          </Title>
          <Text style={{ color: '#64748B', fontSize: 13 }}>
            Nhập thông tin tài khoản để truy cập hệ thống.
          </Text>
        </div>

        {/* Quick Demo Fill Button */}
        <div style={{
          background: 'rgba(99, 102, 241, 0.08)',
          border: '1px solid rgba(99, 102, 241, 0.2)',
          borderRadius: 10,
          padding: '10px 12px',
          marginBottom: 18,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <span style={{ color: '#94A3B8', fontSize: 12 }}>Tài khoản thử nghiệm:</span>
          <Button 
            size="small" 
            onClick={() => fillDemoAccount('haohoangth809@gmail.com', 'admin@123')}
            style={{ 
              fontSize: 12, 
              background: 'rgba(0, 242, 254, 0.1)', 
              borderColor: 'rgba(0, 242, 254, 0.3)',
              color: '#00F2FE',
              fontWeight: 600,
              borderRadius: 6,
            }}
          >
            Điền nhanh tài khoản mẫu
          </Button>
        </div>

        {error && (
          <Alert
            message={error}
            type="error"
            showIcon
            style={{ marginBottom: 18, borderRadius: 10, background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.25)' }}
            closable
            onClose={() => setError(null)}
          />
        )}

        <Form form={form} layout="vertical" onFinish={handleSubmit} requiredMark={false}>
          <Form.Item
            name="username"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Email Đăng Nhập</Text>}
            rules={[{ required: true, message: 'Vui lòng nhập Email của bạn' }]}
          >
            <Input 
              prefix={<UserOutlined style={{ color: '#64748B' }}/>} 
              placeholder="Ví dụ: haohoangth809@gmail.com" 
              autoComplete="username"
              style={{ height: 42 }}
            />
          </Form.Item>

          <Form.Item
            name="password"
            label={<Text style={{ color: '#94A3B8', fontWeight: 600, fontSize: 12, textTransform: 'uppercase' }}>Mật Khẩu</Text>}
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: '#64748B' }}/>}
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
              style={{ height: 42 }}
            />
          </Form.Item>

          <Form.Item style={{ marginTop: 20, marginBottom: 10 }}>
            <Button 
              type="primary" 
              htmlType="submit" 
              loading={loading} 
              block 
              style={{ 
                height: 44, 
                fontSize: 15, 
                fontWeight: 700,
                background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                border: 'none',
                boxShadow: '0 4px 16px rgba(99, 102, 241, 0.35)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
              }}
            >
              <span>Vào DenHub</span>
              <ArrowRightOutlined />
            </Button>
          </Form.Item>
        </Form>

        <Divider style={{ borderColor: 'rgba(255, 255, 255, 0.08)', margin: '16px 0' }} />

        <div style={{ textAlign: 'center' }}>
          <Text style={{ color: '#94A3B8', fontSize: 13 }}>
            Chưa có tài khoản?{' '}
            <Link to="/register" style={{ color: '#00F2FE', fontWeight: 600 }}>Tạo tài khoản mới</Link>
          </Text>
        </div>
      </div>
    </div>
  );
}
