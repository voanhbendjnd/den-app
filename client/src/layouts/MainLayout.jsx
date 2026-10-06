import { useState } from 'react';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import {
  Layout,
  Menu,
  Avatar,
  Badge,
  Dropdown,
  Button,
  Typography,
  Drawer,
} from 'antd';
import {
  HomeOutlined,
  TeamOutlined,
  CalendarOutlined,
  BellOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  MenuOutlined,
  UsergroupAddOutlined,
  AppstoreOutlined,
} from '@ant-design/icons';
import { useAuth } from '../hooks/useAuth';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const SIDEBAR_WIDTH = 220;

function buildMenuItems(role) {
  const items = [
    { key: '/', icon: <HomeOutlined />, label: <Link to="/">Home</Link> },
    { key: '/rooms', icon: <TeamOutlined />, label: <Link to="/rooms">Rooms</Link> },
    { key: '/meetings', icon: <CalendarOutlined />, label: <Link to="/meetings">Meetings</Link> },
    { key: '/notifications', icon: <BellOutlined />, label: <Link to="/notifications">Notifications</Link> },
    { key: '/profile', icon: <UserOutlined />, label: <Link to="/profile">Profile</Link> },
  ];

  if (role === 'ADMIN') {
    items.push(
      { type: 'divider' },
      {
        key: 'admin',
        icon: <SettingOutlined />,
        label: 'Admin',
        children: [
          { key: '/admin/users', icon: <UsergroupAddOutlined />, label: <Link to="/admin/users">Users</Link> },
          { key: '/admin/rooms', icon: <AppstoreOutlined />, label: <Link to="/admin/rooms">Rooms</Link> },
        ],
      }
    );
  }

  return items;
}

export default function MainLayout() {
  const { currentUser, role, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const selectedKey = '/' + location.pathname.split('/').slice(1, 3).join('/') || '/';
  const menuItems = buildMenuItems(role);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: 'Profile',
      onClick: () => navigate('/profile'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      danger: true,
      onClick: handleLogout,
    },
  ];

  const sideMenu = (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Logo */}
      <div
        style={{
          height: 56,
          display: 'flex',
          alignItems: 'center',
          padding: '0 20px',
          borderBottom: '1px solid #f0f0f0',
          flexShrink: 0,
        }}
      >
        <Text strong style={{ fontSize: 18, letterSpacing: '-0.5px', color: '#1677ff' }}>
          DenHub
        </Text>
      </div>

      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        defaultOpenKeys={['admin']}
        items={menuItems}
        style={{ border: 'none', flex: 1, overflow: 'auto' }}
      />
    </div>
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Desktop Sidebar */}
      <Sider
        width={SIDEBAR_WIDTH}
        theme="light"
        style={{
          borderRight: '1px solid #f0f0f0',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
          overflow: 'hidden',
        }}
        breakpoint="lg"
        collapsedWidth={0}
        trigger={null}
      >
        {sideMenu}
      </Sider>

      {/* Mobile Drawer */}
      <Drawer
        placement="left"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={SIDEBAR_WIDTH}
        styles={{ body: { padding: 0 } }}
        title={null}
        closable={false}
      >
        {sideMenu}
      </Drawer>

      <Layout style={{ marginLeft: SIDEBAR_WIDTH }}>
        {/* Header */}
        <Header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 99,
            backgroundColor: '#fff',
            borderBottom: '1px solid #f0f0f0',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: 56,
          }}
        >
          {/* Mobile menu button */}
          <Button
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setDrawerOpen(true)}
            style={{ display: 'none' }}
            className="mobile-menu-btn"
          />

          <div />

          {/* Right side actions */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Badge count={0} size="small">
              <Button
                type="text"
                icon={<BellOutlined />}
                onClick={() => navigate('/notifications')}
              />
            </Badge>

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <div
                style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}
              >
                <Avatar
                  size={32}
                  icon={<UserOutlined />}
                  style={{ backgroundColor: '#1677ff' }}
                />
                <Text style={{ fontSize: 14 }}>{currentUser?.displayName || currentUser?.username}</Text>
              </div>
            </Dropdown>
          </div>
        </Header>

        {/* Main Content */}
        <Content
          style={{
            padding: 24,
            backgroundColor: '#f5f5f5',
            minHeight: 'calc(100vh - 56px)',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
