import { useState, useEffect, useRef } from 'react';
import { Form, Input, Button, Typography, Alert } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

const { Text } = Typography;

export default function LoginPage() {
  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState(null);
  const googleBtnRef = useRef(null);

  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  // Initialize Google Identity Services
  useEffect(() => {
    if (!googleClientId) return;

    const loadGoogleScript = () => {
      if (document.getElementById('google-client-script')) return;
      const script = document.createElement('script');
      script.id = 'google-client-script';
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => {
        if (window.google?.accounts?.id) {
          window.google.accounts.id.initialize({
            client_id: googleClientId,
            callback: handleGoogleResponse,
          });
          if (googleBtnRef.current) {
            window.google.accounts.id.renderButton(googleBtnRef.current, {
              theme: 'filled_black',
              size: 'large',
              width: 414,
              text: 'signin_with',
              shape: 'rectangular',
            });
          }
        }
      };
      document.body.appendChild(script);
    };

    loadGoogleScript();
  }, [googleClientId]);

  const handleGoogleResponse = async (response) => {
    if (!response?.credential) return;
    setGoogleLoading(true);
    setError(null);
    try {
      await loginWithGoogle({ credential: response.credential });
      navigate('/');
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Đăng nhập với Google thất bại. Vui lòng thử lại!';
      setError(msg);
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleCustomGoogleClick = async () => {
    if (window.google?.accounts?.id && googleClientId) {
      window.google.accounts.id.prompt((notification) => {
        if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
          // If prompt blocked, prompt standard fallback
          handleDevGoogleLogin();
        }
      });
    } else {
      // Demo / Developer fallback when client id is not loaded
      await handleDevGoogleLogin();
    }
  };

  const handleDevGoogleLogin = async () => {
    setGoogleLoading(true);
    setError(null);
    try {
      await loginWithGoogle({
        email: 'google.user@denhub.app',
        name: 'Google User',
        avatar: 'https://lh3.googleusercontent.com/a/default-user=s96-c',
        googleId: 'google_demo_101',
      });
      navigate('/');
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Đăng nhập Google thất bại.';
      setError(msg);
    } finally {
      setGoogleLoading(false);
    }
  };

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

  const fillDemoAccount = () => {
    form.setFieldsValue({ username: 'haohoangth809@gmail.com', password: 'admin@123' });
    setError(null);
  };

  return (
    <div className="discord-card" style={{
      maxWidth: 784,
      padding: '32px',
      margin: '0 auto',
    }}>
      <div style={{
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'stretch',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 32,
      }}>
        {/* Left Column: Authentic Discord Login Form */}
        <div style={{
          flex: '1 1 414px',
          maxWidth: 414,
          minWidth: 280,
        }}>
          {/* Title Header */}
          <div style={{ textAlign: 'center', marginBottom: 20 }}>
            <h2 style={{
              margin: '0 0 8px 0',
              color: '#f2f3f5',
              fontSize: 24,
              fontWeight: 600,
              fontFamily: "'Plus Jakarta Sans', sans-serif",
            }}>
              Chào mừng trở lại!
            </h2>
            <div style={{
              color: '#b5bac1',
              fontSize: 16,
              fontWeight: 400,
            }}>
              Rất vui mừng khi được gặp lại bạn!
            </div>
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

          <Form form={form} layout="vertical" onFinish={handleSubmit} requiredMark={false}>
            {/* Field 1: Email / Username */}
            <Form.Item
              name="username"
              style={{ marginBottom: 20 }}
              label={
                <span className="discord-label">
                  Email hoặc Số Điện Thoại <span className="required-star">*</span>
                </span>
              }
              rules={[{ required: true, message: 'Vui lòng nhập Email hoặc tên đăng nhập' }]}
            >
              <Input
                className="discord-input"
                autoComplete="username"
                style={{ height: 40, padding: '10px' }}
              />
            </Form.Item>

            {/* Field 2: Password */}
            <Form.Item
              name="password"
              style={{ marginBottom: 8 }}
              label={
                <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
                  <span className="discord-label" style={{ marginBottom: 0 }}>
                    Mật khẩu <span className="required-star">*</span>
                  </span>
                </div>
              }
              rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
            >
              <Input.Password
                className="discord-input"
                autoComplete="current-password"
                style={{ height: 40, padding: '10px' }}
              />
            </Form.Item>

            {/* Forgot Password Link */}
            <div style={{ marginBottom: 20, marginTop: 4 }}>
              <a
                href="#forgot-password"
                onClick={(e) => { e.preventDefault(); setError('Vui lòng liên hệ quản trị viên để khôi phục mật khẩu.'); }}
                className="discord-link"
                style={{ fontSize: 14 }}
              >
                Quên mật khẩu?
              </a>
            </div>

            {/* Primary Submit Button */}
            <Form.Item style={{ marginBottom: 12 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                className="discord-button-blurple"
              >
                Đăng nhập
              </Button>
            </Form.Item>

            {/* Google Sign In Button */}
            <div style={{ marginBottom: 16 }}>
              <Button
                type="default"
                onClick={handleCustomGoogleClick}
                loading={googleLoading}
                className="discord-button-google"
              >
                {/* Google Official Colorful SVG Logo */}
                <svg width="18" height="18" viewBox="0 0 48 48">
                  <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                  <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                  <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                  <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                </svg>
                <span>Đăng nhập bằng Google</span>
              </Button>
            </div>

            {/* Hidden container for Google official button render */}
            <div ref={googleBtnRef} style={{ display: 'none' }} />

            {/* Footer Registration Link & Demo fill */}
            <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ fontSize: 14 }}>
                <span style={{ color: '#949ba4' }}>Cần một tài khoản? </span>
                <Link to="/register" className="discord-link">Đăng ký</Link>
              </div>

              {/* Quick test filler */}
              <div style={{ marginTop: 4 }}>
                <span
                  onClick={fillDemoAccount}
                  style={{
                    color: '#64748B',
                    fontSize: 12,
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  ⚡ Điền nhanh tài khoản mẫu (demo)
                </span>
              </div>
            </div>
          </Form>
        </div>

        {/* Right Column: Authentic Discord QR Code Login Panel */}
        <div style={{
          flex: '0 0 240px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          textAlign: 'center',
          paddingLeft: 12,
        }}>
          {/* White QR Code Container */}
          <div style={{
            width: 176,
            height: 176,
            backgroundColor: '#ffffff',
            borderRadius: 6,
            padding: 8,
            boxShadow: '0 2px 10px 0 rgba(0, 0, 0, 0.2)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative',
          }}>
            {/* Vector Discord QR Code with Logo Badge in center */}
            <svg width="160" height="160" viewBox="0 0 160 160">
              {/* Corner position markers */}
              {/* Top-Left */}
              <rect x="10" y="10" width="40" height="40" rx="4" fill="#000000" />
              <rect x="16" y="16" width="28" height="28" rx="2" fill="#ffffff" />
              <rect x="22" y="22" width="16" height="16" rx="2" fill="#000000" />

              {/* Top-Right */}
              <rect x="110" y="10" width="40" height="40" rx="4" fill="#000000" />
              <rect x="116" y="16" width="28" height="28" rx="2" fill="#ffffff" />
              <rect x="122" y="22" width="16" height="16" rx="2" fill="#000000" />

              {/* Bottom-Left */}
              <rect x="10" y="110" width="40" height="40" rx="4" fill="#000000" />
              <rect x="16" y="116" width="28" height="28" rx="2" fill="#ffffff" />
              <rect x="22" y="122" width="16" height="16" rx="2" fill="#000000" />

              {/* Dense QR data modules pattern */}
              <rect x="56" y="12" width="8" height="8" fill="#000000" />
              <rect x="72" y="12" width="16" height="8" fill="#000000" />
              <rect x="94" y="12" width="8" height="8" fill="#000000" />
              <rect x="56" y="26" width="14" height="8" fill="#000000" />
              <rect x="80" y="26" width="8" height="14" fill="#000000" />
              <rect x="94" y="26" width="10" height="8" fill="#000000" />

              <rect x="12" y="56" width="8" height="16" fill="#000000" />
              <rect x="26" y="60" width="14" height="8" fill="#000000" />
              <rect x="44" y="56" width="8" height="8" fill="#000000" />
              <rect x="12" y="80" width="16" height="8" fill="#000000" />
              <rect x="36" y="80" width="8" height="16" fill="#000000" />

              <rect x="112" y="56" width="16" height="8" fill="#000000" />
              <rect x="136" y="60" width="12" height="14" fill="#000000" />
              <rect x="116" y="74" width="8" height="16" fill="#000000" />
              <rect x="130" y="80" width="18" height="8" fill="#000000" />

              <rect x="56" y="112" width="8" height="16" fill="#000000" />
              <rect x="70" y="120" width="18" height="8" fill="#000000" />
              <rect x="94" y="112" width="8" height="8" fill="#000000" />
              <rect x="56" y="136" width="16" height="12" fill="#000000" />
              <rect x="80" y="132" width="10" height="16" fill="#000000" />
              <rect x="100" y="138" width="8" height="10" fill="#000000" />

              <rect x="112" y="112" width="14" height="8" fill="#000000" />
              <rect x="132" y="116" width="16" height="8" fill="#000000" />
              <rect x="120" y="130" width="8" height="18" fill="#000000" />
              <rect x="136" y="136" width="12" height="12" fill="#000000" />

              {/* Center White Badge with Discord Mascot Icon */}
              <circle cx="80" cy="80" r="22" fill="#ffffff" />
              <circle cx="80" cy="80" r="18" fill="#5865F2" />
              <path
                d="M87.5,74.5 A15,15,0,0,0,83,73 a10,10,0,0,0-.5,1 A13,13,0,0,0,74.5,74 a10,10,0,0,0-.5-1 A15,15,0,0,0,69.5,74.5 C67,78 66.5,81.5 66.8,85 A15,15,0,0,0,71.5,87.5 a11,11,0,0,0,1-1.6 a10,10,0,0,1-1.5-.7 c.1-.1.3-.2.4-.3 a11,11,0,0,0,9.2,0 c.1.1.3.2.4.3 a10,10,0,0,1-1.5.7 a11,11,0,0,0,1,1.6 A15,15,0,0,0,86.2,85 C86.6,81.5 85.5,78 87.5,74.5 Z M72.8,83 A1.7,1.7,0,1,1,74.5,81.3,1.7,1.7,0,0,1,72.8,83 Z M81.2,83 A1.7,1.7,0,1,1,82.9,81.3,1.7,1.7,0,0,1,81.2,83 Z"
                fill="#ffffff"
              />
            </svg>
          </div>

          {/* QR Code Title */}
          <h3 style={{
            margin: '24px 0 8px 0',
            color: '#f2f3f5',
            fontSize: 24,
            fontWeight: 600,
            fontFamily: "'Plus Jakarta Sans', sans-serif",
          }}>
            Đăng nhập bằng Mã QR
          </h3>

          {/* QR Code Instructions */}
          <p style={{
            color: '#b5bac1',
            fontSize: 14,
            lineHeight: '20px',
            margin: 0,
            maxWidth: 220,
          }}>
            Quét bằng <strong style={{ color: '#ffffff' }}>ứng dụng di động Discord</strong> để đăng nhập tức thì.
          </p>

          {/* QR Code Alternate Link */}
          <a
            href="#passkey"
            onClick={(e) => { e.preventDefault(); setError('Tính năng đăng nhập bằng mã khóa đang được phát triển.'); }}
            className="discord-link"
            style={{ marginTop: 12, fontSize: 14 }}
          >
            Hoặc đăng nhập bằng mã khóa
          </a>
        </div>
      </div>
    </div>
  );
}
