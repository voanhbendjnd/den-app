import { useState } from 'react';
import { Form, Input, Button, Alert, Modal } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import authApi from '../../api/authApi';
import { useAuth } from '../../hooks/useAuth';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { loginWithGoogle, loginWithSocial } = useAuth();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [githubLoading, setGithubLoading] = useState(false);
  const [githubModalOpen, setGithubModalOpen] = useState(false);
  const defaultGithubToken = import.meta.env.VITE_GITHUB_TOKEN;
  const [githubTokenInput, setGithubTokenInput] = useState(defaultGithubToken || '');
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const githubClientId = import.meta.env.VITE_GITHUB_CLIENT_ID;

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

  const handleGithubRegisterClick = async () => {
    if (defaultGithubToken) {
      setGithubLoading(true);
      setError(null);
      try {
        await loginWithSocial({
          accessToken: defaultGithubToken.trim(),
          type: 'GITHUB',
        });
        navigate('/');
      } catch (err) {
        const msg = err.response?.data?.message || err.message || 'Đăng ký bằng GitHub thất bại.';
        setError(msg);
      } finally {
        setGithubLoading(false);
      }
      return;
    }
    if (githubClientId) {
      const redirectUri = encodeURIComponent(window.location.origin + '/login');
      window.location.href = `https://github.com/login/oauth/authorize?client_id=${githubClientId}&scope=read:user,user:email`;
    } else {
      setGithubModalOpen(true);
    }
  };

  const handleGithubTokenRegister = async () => {
    if (!githubTokenInput || !githubTokenInput.trim()) {
      setError('Vui lòng nhập GitHub Personal Access Token');
      return;
    }
    setGithubLoading(true);
    setError(null);
    try {
      await loginWithSocial({
        accessToken: githubTokenInput.trim(),
        type: 'GITHUB',
      });
      setGithubModalOpen(false);
      navigate('/');
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Đăng ký bằng GitHub thất bại. Kiểm tra lại Access Token!';
      setError(msg);
    } finally {
      setGithubLoading(false);
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

        {/* Divider */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          margin: '16px 0 14px 0',
          color: '#949ba4',
          fontSize: 12,
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}>
          <div style={{ flex: 1, height: 1, backgroundColor: '#3f4147' }} />
          <span style={{ padding: '0 10px' }}>Hoặc đăng ký với</span>
          <div style={{ flex: 1, height: 1, backgroundColor: '#3f4147' }} />
        </div>

        {/* Social Register Buttons Grid: Google & GitHub */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 16 }}>
          {/* Google Button */}
          <Button
            type="default"
            onClick={handleGoogleRegister}
            loading={googleLoading}
            className="discord-button-google"
          >
            <svg width="18" height="18" viewBox="0 0 48 48">
              <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
              <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
              <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
            </svg>
            <span>Google</span>
          </Button>

          {/* GitHub Button */}
          <Button
            type="default"
            onClick={handleGithubRegisterClick}
            loading={githubLoading}
            className="discord-button-github"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="#f2f3f5">
              <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
            </svg>
            <span>GitHub</span>
          </Button>
        </div>

        {/* Optional: manual token config link */}
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <span
            onClick={() => setGithubModalOpen(true)}
            style={{ color: '#949ba4', cursor: 'pointer', fontSize: 12, textDecoration: 'underline' }}
          >
            ⚙️ Đổi / Nhập GitHub Token thủ công
          </span>
        </div>

        {/* Footer Link to Login */}
        <div style={{ marginTop: 12, fontSize: 14 }}>
          <Link to="/login" className="discord-link">
            Đã có tài khoản?
          </Link>
        </div>
      </Form>

      {/* GitHub Token Modal for Fast Register */}
      <Modal
        title={
          <div style={{ color: '#f2f3f5', display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="#f2f3f5">
              <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
            </svg>
            <span>Đăng ký nhanh bằng GitHub</span>
          </div>
        }
        open={githubModalOpen}
        onCancel={() => setGithubModalOpen(false)}
        footer={null}
        styles={{
          content: {
            backgroundColor: '#313338',
            color: '#f2f3f5',
            borderRadius: 8,
            border: '1px solid #3f4147',
          },
          header: {
            backgroundColor: '#313338',
            borderBottom: '1px solid #3f4147',
            paddingBottom: 12,
          },
        }}
      >
        <div style={{ marginTop: 12 }}>
          <p style={{ color: '#b5bac1', fontSize: 14 }}>
            Nhập <strong>GitHub Access Token</strong> (Personal Access Token có quyền <code>read:user</code> và <code>user:email</code>) để tạo tài khoản tức thì:
          </p>
          <Input.Password
            className="discord-input"
            placeholder="ghp_xxxxxxxxxxxxxxxxxxxx"
            value={githubTokenInput}
            onChange={(e) => setGithubTokenInput(e.target.value)}
            style={{ height: 40, marginBottom: 16 }}
          />
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <Button
              onClick={() => setGithubModalOpen(false)}
              style={{ backgroundColor: 'transparent', color: '#b5bac1', borderColor: '#4e5058' }}
            >
              Hủy
            </Button>
            <Button
              type="primary"
              className="discord-button-blurple"
              loading={githubLoading}
              onClick={handleGithubTokenRegister}
            >
              Xác thực & Tạo tài khoản
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
