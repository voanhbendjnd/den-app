import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

export default function AuthLayout() {
  const { isAuthenticated } = useAuth();

  // Already logged in — go to home
  if (isAuthenticated) return <Navigate to="/" replace />;

  return (
    <div
      className="tech-grid-bg"
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <Outlet />
    </div>
  );
}
