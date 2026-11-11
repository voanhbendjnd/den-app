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
        <Link to="/login" style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
          {/* Discord SVG Controller Icon */}
          <svg width="34" height="26" viewBox="0 0 127.14 96.36" fill="#ffffff">
            <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.7,77.7,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.25,105.25,0,0,0,126.6,80.22h0C129.24,52.84,122.09,29.11,107.7,8.07ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,45.91,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,45.91,96.12,53,91.08,65.69,84.69,65.69Z" />
          </svg>
          <span style={{
            color: '#ffffff',
            fontWeight: 800,
            fontSize: 20,
            letterSpacing: '-0.3px',
            fontFamily: "'Plus Jakarta Sans', sans-serif"
          }}>
            Den hub
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
