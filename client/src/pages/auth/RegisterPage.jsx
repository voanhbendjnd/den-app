import { useState } from 'react';
import { Form, Input, Button, Alert } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import authApi from '../../api/authApi';
import { useAuth } from '../../hooks/useAuth';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { loginWithGoogle } = useAuth();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const handleSubmit = async (values) => {
    setLoading(true);
    setError(null);
    setSuccessMsg(null);
    try {
      const payload = {
        email: values.email,
        password: values.password,
        name: values.displayName || values.username || values.email.split('@')[0],
        username: values.username,
      };
      await authApi.register(payload);
      setSuccessMsg('Đăng ký tài khoản thành công! Đang chuyển hướng đến đăng nhập...');
      setTimeout(() => {
        navigate('/login');
      }, 1200);
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        err.response?.data?.error ||
        'Đăng ký tài khoản không thành công. Vui lòng kiểm tra lại.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleRegister = async () => {
    setGoogleLoading(true);
    setError(null);
    try {
      await loginWithGoogle({
        email: 'new.google.user@denhub.app',
        name: 'Google User',
        avatar: 'https://lh3.googleusercontent.com/a/default-user=s96-c',
        googleId: 'google_register_demo_102',
      });
      navigate('/');
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Đăng ký nhanh bằng Google thất bại.';
      setError(msg);
    } finally {
      setGoogleLoading(false);
    }
  };

  return (
    <div className="discord-card" style={{
      maxWidth: 480,
      padding: '32px',
      margin: '0 auto',
    }}>
      {/* Title Header */}
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <h2 style={{
          margin: 0,
          color: '#f2f3f5',
          fontSize: 24,
          fontWeight: 600,
          fontFamily: "'Plus Jakarta Sans', sans-serif",
        }}>
          Tạo tài khoản
        </h2>
      </div>

      {error && (
        <Alert
          message={error}
          type="error"
          showIcon
          style={{
            marginBottom: 16,
            backgroundColor: 'rgba(242, 63, 67, 0.15)',
            border: '1px solid rgba(242, 63, 67, 0.3)',
            color: '#f23f43',
            borderRadius: 4,
          }}
          closable
          onClose={() => setError(null)}
        />
      )}

      {successMsg && (
        <Alert
          message={successMsg}
          type="success"
          showIcon
          style={{
            marginBottom: 16,
            backgroundColor: 'rgba(35, 165, 90, 0.15)',
            border: '1px solid rgba(35, 165, 90, 0.3)',
            color: '#23a55a',
            borderRadius: 4,
          }}
        />
      )}

      <Form form={form} layout="vertical" onFinish={handleSubmit} requiredMark={false}>
        {/* Field 1: Email */}
        <Form.Item
          name="email"
          style={{ marginBottom: 18 }}
          label={
            <span className="discord-label">
              Email <span className="required-star">*</span>
            </span>
          }
          rules={[
            { required: true, message: 'Vui lòng nhập email' },
            { type: 'email', message: 'Email không đúng định dạng' },
          ]}
        >
          <Input
            className="discord-input"
            autoComplete="email"
            style={{ height: 40, padding: '10px' }}
          />
        </Form.Item>

        {/* Field 2: Display Name */}
        <Form.Item
          name="displayName"
          style={{ marginBottom: 18 }}
          label={
            <span className="discord-label">
              Tên hiển thị
            </span>
          }
        >
          <Input
            className="discord-input"
            placeholder="Người khác sẽ nhìn thấy bạn với tên này"
            style={{ height: 40, padding: '10px' }}
          />
        </Form.Item>

        {/* Field 3: Username */}
        <Form.Item
          name="username"
          style={{ marginBottom: 18 }}
          label={
            <span className="discord-label">
              Tên đăng nhập <span className="required-star">*</span>
            </span>
          }
          rules={[
            { required: true, message: 'Vui lòng nhập tên đăng nhập' },
            { min: 3, message: 'Tên đăng nhập tối thiểu 3 ký tự' },
          ]}
        >
          <Input
            className="discord-input"
            autoComplete="username"
            style={{ height: 40, padding: '10px' }}
          />
        </Form.Item>

        {/* Field 4: Password */}
        <Form.Item
          name="password"
          style={{ marginBottom: 24 }}
          label={
            <span className="discord-label">
              Mật khẩu <span className="required-star">*</span>
            </span>
          }
          rules={[
            { required: true, message: 'Vui lòng nhập mật khẩu' },
            { min: 6, message: 'Mật khẩu tối thiểu 6 ký tự' },
          ]}
        >
          <Input.Password
            className="discord-input"
            autoComplete="new-password"
            style={{ height: 40, padding: '10px' }}
          />
        </Form.Item>

        {/* Submit Button */}
        <Form.Item style={{ marginBottom: 12 }}>
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            className="discord-button-blurple"
          >
            Tiếp tục
          </Button>
        </Form.Item>

        {/* Google Fast Register Button */}
        <div style={{ marginBottom: 16 }}>
          <Button
            type="default"
            onClick={handleGoogleRegister}
            loading={googleLoading}
            className="discord-button-google"
          >
            <svg width="18" height="18" viewBox="0 0 48 48">
              <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
              <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
              <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
            </svg>
            <span>Đăng ký nhanh bằng Google</span>
          </Button>
        </div>

        {/* Footer Link to Login */}
        <div style={{ marginTop: 12, fontSize: 14 }}>
          <Link to="/login" className="discord-link">
            Đã có tài khoản?
          </Link>
        </div>
      </Form>
    </div>
  );
}
