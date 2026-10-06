import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import PageLoading from '../components/common/PageLoading';

/**
 * Wraps admin-only routes.
 * The backend will enforce authorization — this is UI-level only.
 */
export default function AdminRoute() {
  const { isAuthenticated, role, loading } = useAuth();

  if (loading) return <PageLoading />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (role !== 'ADMIN') return <Navigate to="/403" replace />;

  return <Outlet />;
}
