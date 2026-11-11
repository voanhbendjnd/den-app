import { Outlet, Navigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

export default function AuthLayout() {
  const { isAuthenticated } = useAuth();

  // Already logged in — redirect to home
  if (isAuthenticated) return <Navigate to="/" replace />;

  return (
    <div className="discord-auth-bg">
      {/* Top Left Discord Brand */}
      <div style={{
        position: 'absolute',
        top: 32,
        left: 36,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        zIndex: 20,
      }}>
        <Link to="/login" style={{ display: 'flex', alignItems: 'center', gap: 12, textDecoration: 'none' }}>
          <img
            src="/den-logo.png"
            alt="DenHub Logo"
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              objectFit: 'cover',
              boxShadow: '0 2px 8px rgba(0, 242, 254, 0.25)',
            }}
          />
          <span style={{
            color: '#ffffff',
            fontWeight: 800,
            fontSize: 22,
            letterSpacing: '-0.3px',
            fontFamily: "'Plus Jakarta Sans', sans-serif"
          }}>
            DenHub
          </span>
        </Link>
      </div>

      {/* Decorative Cosmic Floating Shapes & Stars */}
      <div style={{
        position: 'absolute',
        top: '15%',
        left: '8%',
        width: 140,
        height: 140,
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(88, 101, 242, 0.4) 0%, rgba(88, 101, 242, 0.05) 70%, transparent 100%)',
        filter: 'blur(30px)',
        pointerEvents: 'none',
      }} />

      <div style={{
        position: 'absolute',
        bottom: '20%',
        right: '10%',
        width: 220,
        height: 220,
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(88, 101, 242, 0.35) 0%, rgba(41, 50, 110, 0.1) 70%, transparent 100%)',
        filter: 'blur(45px)',
        pointerEvents: 'none',
      }} />

      {/* Glowing Starbursts */}
      <div style={{
        position: 'absolute',
        top: '25%',
        right: '18%',
        color: 'rgba(255, 255, 255, 0.35)',
        fontSize: 22,
        pointerEvents: 'none',
        userSelect: 'none',
      }}>✦</div>

      <div style={{
        position: 'absolute',
        bottom: '30%',
        left: '15%',
        color: 'rgba(88, 101, 242, 0.5)',
        fontSize: 18,
        pointerEvents: 'none',
        userSelect: 'none',
      }}>✦</div>

      <div style={{
        position: 'absolute',
        top: '65%',
        right: '22%',
        color: 'rgba(255, 255, 255, 0.25)',
        fontSize: 14,
        pointerEvents: 'none',
        userSelect: 'none',
      }}>✦</div>

      {/* Main Auth View (Outlet) */}
      <Outlet />
    </div>
  );
}
