import { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Typography, Tooltip } from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  PlusOutlined,
  ThunderboltFilled,
  HomeOutlined,
} from '@ant-design/icons';
import { useAuth } from '../hooks/useAuth';
import CreateServerModal from '../components/common/CreateServerModal';
import serverApi from '../api/serverApi';

const { Sider, Content } = Layout;
const { Text } = Typography;

export default function MainLayout() {
  const { currentUser, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [servers, setServers] = useState([]);

  useEffect(() => {
    fetchServers();
  }, []);

  const fetchServers = async () => {
    try {
      const res = await serverApi.getAll();
      const serverList = res.data?.data || res.data || [];
      if (Array.isArray(serverList)) {
        setServers(serverList);
      }
    } catch {
      // Ignored for fallback
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: 'Hồ sơ cá nhân',
      onClick: () => navigate('/profile'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Đăng xuất',
      danger: true,
      onClick: handleLogout,
    },
  ];

  // Chỉ giữ các route thực sự hoạt động trong MVP
  const secondaryMenuItems = [
    { 
      key: '/', 
      icon: <HomeOutlined style={{ fontSize: 16 }} />, 
      label: <Link to="/" style={{ fontWeight: 600 }}>Trang Tổng Quan</Link> 
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh', background: 'var(--color-bg-base)' }}>
      {/* =========================================================
          Primary Dock (Far-Left Server Launcher)
         ========================================================= */}
      <Sider
        width={72}
        style={{
          background: 'var(--color-bg-sidebar)',
          borderRight: '1px solid var(--border-subtle)',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          paddingTop: 14,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, width: '100%' }}>
          {/* Main Home Hub Emblem */}
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {location.pathname === '/' && (
              <div style={{
                position: 'absolute',
                left: -12,
                width: 4,
                height: 36,
                borderRadius: '0 4px 4px 0',
                background: '#00F2FE',
                boxShadow: '0 0 10px #00F2FE',
              }} />
            )}
            <Tooltip title="Trang Chủ DenHub" placement="right">
              <div 
                style={{
                  width: 48,
                  height: 48,
                  borderRadius: location.pathname === '/' ? 16 : 24,
                  background: location.pathname === '/' 
                    ? 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)' 
                    : 'var(--color-bg-surface)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                  boxShadow: location.pathname === '/' ? '0 4px 16px rgba(0, 242, 254, 0.35)' : 'none',
                  border: location.pathname === '/' ? 'none' : '1px solid var(--border-subtle)',
                }}
                onClick={() => navigate('/')}
              >
                <ThunderboltFilled style={{ 
                  fontSize: 22, 
                  color: location.pathname === '/' ? '#fff' : '#00F2FE' 
                }} />
              </div>
            </Tooltip>
          </div>

          {/* Separator Line */}
          <div style={{ width: 32, height: 2, background: 'var(--border-subtle)', borderRadius: 1 }} />

          {/* User's Created Servers */}
          <div style={{ 
            display: 'flex', 
            flexDirection: 'column', 
            gap: 10, 
            width: '100%', 
            alignItems: 'center',
            maxHeight: 'calc(100vh - 200px)',
            overflowY: 'auto',
            overflowX: 'hidden'
          }}>
            {servers.map((server) => {
              const isServerActive = location.pathname.startsWith(`/servers/${server.id}`);
              return (
                <div key={server.id} style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {isServerActive && (
                    <div style={{
                      position: 'absolute',
                      left: -12,
                      width: 4,
                      height: 36,
                      borderRadius: '0 4px 4px 0',
                      background: '#00F2FE',
                      boxShadow: '0 0 10px #00F2FE',
                    }} />
                  )}
                  <Tooltip title={server.name} placement="right">
                    <div 
                      onClick={() => navigate('/')}
                      style={{
                        width: 48,
                        height: 48,
                        borderRadius: isServerActive ? 16 : 24,
                        background: 'var(--color-bg-surface)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                        border: '1px solid var(--border-subtle)',
                        overflow: 'hidden',
                        color: '#F1F5F9',
                        fontWeight: 700,
                        fontSize: 16,
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.borderRadius = '16px';
                        e.currentTarget.style.borderColor = '#6366F1';
                        e.currentTarget.style.boxShadow = '0 0 12px rgba(99, 102, 241, 0.3)';
                      }}
                      onMouseLeave={(e) => {
                        if (!isServerActive) {
                          e.currentTarget.style.borderRadius = '24px';
                          e.currentTarget.style.borderColor = 'var(--border-subtle)';
                          e.currentTarget.style.boxShadow = 'none';
                        }
                      }}
                    >
                      {server.iconUrl ? (
                        <img 
                          src={server.iconUrl} 
                          alt={server.name} 
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                        />
                      ) : (
                        <span>{server.name ? server.name.charAt(0).toUpperCase() : 'S'}</span>
                      )}
                    </div>
                  </Tooltip>
                </div>
              );
            })}

            {/* Quick Action: Add Server */}
            <Tooltip title="Tạo Không Gian Mới (Server)" placement="right">
              <div 
                onClick={() => setIsCreateModalOpen(true)}
                style={{
                  width: 48,
                  height: 48,
                  borderRadius: 24,
                  background: 'rgba(0, 242, 254, 0.08)',
                  color: '#00F2FE',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                  border: '1px dashed rgba(0, 242, 254, 0.4)',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderRadius = '16px';
                  e.currentTarget.style.background = 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)';
                  e.currentTarget.style.color = '#fff';
                  e.currentTarget.style.boxShadow = '0 4px 16px rgba(0, 242, 254, 0.4)';
                  e.currentTarget.style.border = '1px solid transparent';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderRadius = '24px';
                  e.currentTarget.style.background = 'rgba(0, 242, 254, 0.08)';
                  e.currentTarget.style.color = '#00F2FE';
                  e.currentTarget.style.boxShadow = 'none';
                  e.currentTarget.style.border = '1px dashed rgba(0, 242, 254, 0.4)';
                }}
              >
                <PlusOutlined style={{ fontSize: 20 }} />
              </div>
            </Tooltip>
          </div>
        </div>
      </Sider>

      {/* =========================================================
          Secondary Sidebar (Navigation)
         ========================================================= */}
      <Sider
        width={240}
        style={{
          background: 'var(--color-bg-layout)',
          borderRight: '1px solid var(--border-subtle)',
          position: 'fixed',
          left: 72,
          top: 0,
          bottom: 0,
          zIndex: 99,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {/* Workspace Brand Header */}
        <div style={{
          height: 56,
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          padding: '0 18px',
          background: 'rgba(15, 20, 34, 0.6)',
        }}>
          <Text style={{ color: '#F1F5F9', fontWeight: 800, fontSize: 16, letterSpacing: '-0.3px' }}>
            DenHub
          </Text>
        </div>
        
        {/* Navigation Menu */}
        <div style={{ flex: 1, padding: '16px 8px', overflowY: 'auto' }}>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={secondaryMenuItems}
            style={{ borderRight: 0, background: 'transparent' }}
          />
        </div>

        {/* Bottom User Bar */}
        <div style={{
          height: 60,
          background: 'var(--color-bg-sidebar)',
          borderTop: '1px solid var(--border-subtle)',
          padding: '0 14px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden' }}>
            <Avatar 
              size={36} 
              style={{ 
                background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                fontWeight: 700,
                color: '#fff',
                flexShrink: 0,
              }} 
              icon={<UserOutlined />}
            >
              {currentUser?.displayName ? currentUser.displayName.charAt(0).toUpperCase() : 'U'}
            </Avatar>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2, overflow: 'hidden' }}>
              <Text style={{ 
                color: '#F1F5F9', 
                fontWeight: 700, 
                fontSize: 13, 
                whiteSpace: 'nowrap', 
                textOverflow: 'ellipsis', 
                overflow: 'hidden' 
              }}>
                {currentUser?.displayName || currentUser?.username || 'Thành viên'}
              </Text>
              <Text style={{ color: '#64748B', fontSize: 11, whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                {currentUser?.email || 'online'}
              </Text>
            </div>
          </div>

          <Dropdown menu={{ items: userMenuItems }} placement="topRight" trigger={['click']}>
            <div 
              style={{ 
                width: 32, 
                height: 32, 
                borderRadius: 8, 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center', 
                cursor: 'pointer', 
                color: '#94A3B8',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'rgba(255, 255, 255, 0.08)';
                e.currentTarget.style.color = '#F1F5F9';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = '#94A3B8';
              }}
            >
              <SettingOutlined style={{ fontSize: 17 }} />
            </div>
          </Dropdown>
        </div>
      </Sider>

      {/* =========================================================
          Main Content Workspace
         ========================================================= */}
      <Layout style={{ marginLeft: 312, background: 'var(--color-bg-base)', minHeight: '100vh' }}>
        <Content style={{ position: 'relative', height: '100vh', overflowY: 'auto' }}>
          <Outlet context={{ servers, setServers, openCreateModal: () => setIsCreateModalOpen(true), refreshServers: fetchServers }} />
        </Content>
      </Layout>

      {/* Create Server Modal (Single Source of Truth) */}
      <CreateServerModal 
        open={isCreateModalOpen} 
        onClose={() => setIsCreateModalOpen(false)} 
        onSuccess={(newServer) => {
          setServers(prev => [newServer, ...prev.filter(s => s.id !== newServer.id)]);
        }}
      />
    </Layout>
  );
}
