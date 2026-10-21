import { useState, useEffect, useRef } from 'react';
import { Outlet, useNavigate, useLocation, useMatch, Link } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Typography, Tooltip, Spin } from 'antd';
import {
    UserOutlined,
    LogoutOutlined,
    SettingOutlined,
    PlusOutlined,
    ThunderboltFilled,
    HomeOutlined,
    MessageOutlined,
    CompassOutlined,
    TeamOutlined,
} from '@ant-design/icons';
import { useAuth } from '../hooks/useAuth';
import CreateServerModal from '../components/common/CreateServerModal';
import JoinServerModal from '../components/common/JoinServerModal';
import CreateChannelModal from '../components/common/CreateChannelModal';
import serverApi from '../api/serverApi';
import conversationApi from '../api/conversationApi';
import { useGlobalFriendNotification } from '../hooks/useGlobalFriendNotification';

const { Sider, Content } = Layout;
const { Text } = Typography;

export default function MainLayout() {
    useGlobalFriendNotification();
    const [pendingFriendCount, setPendingFriendCount] = useState(0);
    const { currentUser, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const groupMatch = useMatch('/servers/:serverId');
    const isGroupPage = Boolean(groupMatch);
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isJoinModalOpen, setIsJoinModalOpen] = useState(false);
    const [isCreateChannelModalOpen, setIsCreateChannelModalOpen] = useState(false);
    const [targetChannelServer, setTargetChannelServer] = useState(null);
    const [servers, setServers] = useState([]);
    const [serversLoading, setServersLoading] = useState(true);
    const [serversError, setServersError] = useState(null);
    const serversRequest = useRef(0);
    const [conversations, setConversations] = useState([]);
    const [conversationsLoading, setConversationsLoading] = useState(true);
    const conversationsRequest = useRef(0);

    useEffect(() => {
        fetchServers();
        fetchConversations();
        return () => {
            serversRequest.current += 1;
            conversationsRequest.current += 1;
        };
    }, []);

    useEffect(() => {
        const handleReceived = () => setPendingFriendCount((prev) => prev + 1);
        window.addEventListener('denhub:friend-request-received', handleReceived);
        return () => {
            window.removeEventListener('denhub:friend-request-received', handleReceived);
        };
    }, []);

    useEffect(() => {
        if (location.pathname === '/friends') {
            setPendingFriendCount(0);
        }
    }, [location.pathname]);

    const fetchConversations = async () => {
        const request = ++conversationsRequest.current;
        setConversationsLoading(true);
        try {
            const res = await conversationApi.getAll();
            const list = res.data?.data || res.data || [];
            if (request !== conversationsRequest.current) return;
            setConversations(Array.isArray(list) ? list : []);
        } catch {
            if (request === conversationsRequest.current) setConversations([]);
        } finally {
            if (request === conversationsRequest.current) setConversationsLoading(false);
        }
    };

    const fetchServers = async () => {
        const request = ++serversRequest.current;
        setServersLoading(true);
        setServersError(null);
        try {
            const res = await serverApi.getAll();
            const serverList = res.data?.data || res.data || [];
            if (request !== serversRequest.current) return;
            if (Array.isArray(serverList)) {
                setServers(serverList);
            } else {
                setServersError('Không thể tải danh sách nhóm. Vui lòng thử lại.');
            }
        } catch {
            if (request === serversRequest.current) {
                setServersError('Không thể tải danh sách nhóm. Vui lòng thử lại.');
            }
        } finally {
            if (request === serversRequest.current) setServersLoading(false);
        }
    };

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const handleServerDeleted = (id) => {
        // Invalidate list snapshots started before DELETE completed.
        serversRequest.current += 1;
        setServers(previous => previous.filter(item => String(item.id) !== String(id)));
        if (serversLoading) {
            fetchServers();
            return;
        }
        setServersLoading(false);
        setServersError(null);
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
        {
            key: '/direct',
            icon: <MessageOutlined style={{ fontSize: 16 }} />,
            label: <Link to="/direct" style={{ fontWeight: 600 }}>Tin Nhắn Trực Tiếp</Link>
        },
        {
            key: '/friends',
            icon: <TeamOutlined style={{ fontSize: 16 }} />,
            label: (
                <Link to="/friends" style={{ fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span>Bạn Bè</span>
                    {pendingFriendCount > 0 && (
                        <span style={{
                            background: '#EF4444',
                            color: '#fff',
                            fontSize: 10,
                            fontWeight: 800,
                            padding: '1px 6px',
                            borderRadius: 10,
                            lineHeight: '14px',
                            marginLeft: 8,
                        }}>
                            {pendingFriendCount}
                        </span>
                    )}
                </Link>
            )
        },
    ];

    return (
        <Layout className={isGroupPage ? 'group-layout' : undefined} style={{ minHeight: '100vh', background: 'var(--color-bg-base)' }}>
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
                            <Link
                                to="/"
                                aria-label="Trang chủ DenHub"
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
                            >
                                <ThunderboltFilled style={{
                                    fontSize: 22,
                                    color: location.pathname === '/' ? '#fff' : '#00F2FE'
                                }} />
                            </Link>
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
                            const isServerActive = groupMatch?.params.serverId === String(server.id);
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
                                        <Link
                                            to={`/servers/${server.id}`}
                                            aria-label={`Mở nhóm ${server.name}`}
                                            aria-current={isServerActive ? 'page' : undefined}
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
                                        </Link>
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

                        {/* Quick Action: Join Server */}
                        <Tooltip title="Tham Gia Server (ID)" placement="right">
                            <div
                                onClick={() => setIsJoinModalOpen(true)}
                                style={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 24,
                                    background: 'rgba(99, 102, 241, 0.08)',
                                    color: '#818CF8',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    cursor: 'pointer',
                                    transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                                    border: '1px dashed rgba(99, 102, 241, 0.4)',
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.borderRadius = '16px';
                                    e.currentTarget.style.background = 'linear-gradient(135deg, rgba(99, 102, 241, 0.25) 0%, rgba(0, 242, 254, 0.25) 100%)';
                                    e.currentTarget.style.color = '#00F2FE';
                                    e.currentTarget.style.boxShadow = '0 4px 16px rgba(99, 102, 241, 0.35)';
                                    e.currentTarget.style.borderColor = '#00F2FE';
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.borderRadius = '24px';
                                    e.currentTarget.style.background = 'rgba(99, 102, 241, 0.08)';
                                    e.currentTarget.style.color = '#818CF8';
                                    e.currentTarget.style.boxShadow = 'none';
                                    e.currentTarget.style.borderColor = 'rgba(99, 102, 241, 0.4)';
                                }}
                            >
                                <CompassOutlined style={{ fontSize: 20 }} />
                            </div>
                        </Tooltip>
                    </div>
                </div>
            </Sider>

            {/* =========================================================
          Secondary Sidebar (Navigation)
         ========================================================= */}
            {!isGroupPage && <Sider
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

                {/* Navigation Menu + DM List */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'hidden' }}>
                    {/* Nav Items */}
                    <div style={{ padding: '16px 8px 8px' }}>
                    <Menu
                            mode="inline"
                            selectedKeys={[
                                location.pathname.startsWith('/direct') ? '/direct'
                                : location.pathname === '/friends' ? '/friends'
                                : location.pathname
                            ]}
                            items={secondaryMenuItems}
                            style={{ borderRight: 0, background: 'transparent' }}
                        />
                    </div>

                    {/* DM Section */}
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'hidden', padding: '0 0 8px' }}>
                        {/* DM Header */}
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '6px 16px 6px 18px',
                            marginBottom: 2,
                        }}>
              <span style={{
                  color: '#64748B',
                  fontSize: 11,
                  fontWeight: 700,
                  letterSpacing: '0.06em',
                  textTransform: 'uppercase',
                  userSelect: 'none',
              }}>
                Tin nhắn trực tiếp
              </span>
                            <Tooltip title="Tạo tin nhắn mới" placement="right">
                                <div
                                    onClick={() => navigate('/direct')}
                                    style={{
                                        width: 20,
                                        height: 20,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        cursor: 'pointer',
                                        color: '#64748B',
                                        borderRadius: 4,
                                        transition: 'all 0.15s',
                                        fontSize: 16,
                                        lineHeight: 1,
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.color = '#F1F5F9';
                                        e.currentTarget.style.background = 'rgba(255,255,255,0.08)';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.color = '#64748B';
                                        e.currentTarget.style.background = 'transparent';
                                    }}
                                >
                                    +
                                </div>
                            </Tooltip>
                        </div>

                        {/* DM Items */}
                        <div style={{ flex: 1, overflowY: 'auto', padding: '0 6px' }}>
                            {conversationsLoading ? (
                                <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 24 }}>
                                    <Spin size="small" />
                                </div>
                            ) : conversations.length === 0 ? (
                                <div style={{ padding: '12px 12px', color: '#475569', fontSize: 12, textAlign: 'center' }}>
                                    Chưa có cuộc trò chuyện nào.
                                </div>
                            ) : (
                                conversations.map((conv) => {
                                    const other = conv.otherUser;
                                    const convId = conv.conversationId;
                                    // Hội thoại đang mở được xác định hoàn toàn bằng URL /direct/:id
                                    const isActive = location.pathname === `/direct/${convId}`;
                                    const displayName = other?.name || other?.username || 'Unknown';
                                    const initial = displayName.trim()[0]?.toUpperCase() || '?';

                                    return (
                                        <div
                                            key={convId}
                                            onClick={() => navigate(`/direct/${convId}`)}
                                            style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: 10,
                                                padding: '7px 10px',
                                                borderRadius: 8,
                                                cursor: 'pointer',
                                                background: isActive ? 'rgba(99,102,241,0.18)' : 'transparent',
                                                transition: 'background 0.15s',
                                                marginBottom: 1,
                                            }}
                                            onMouseEnter={(e) => {
                                                if (!isActive) e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
                                            }}
                                            onMouseLeave={(e) => {
                                                if (!isActive) e.currentTarget.style.background = 'transparent';
                                            }}
                                        >
                                            {/* Avatar + online dot */}
                                            <div style={{ position: 'relative', flexShrink: 0 }}>
                                                <Avatar
                                                    size={32}
                                                    src={other?.avatar}
                                                    style={{
                                                        background: 'linear-gradient(135deg, #6366F1, #00F2FE)',
                                                        fontWeight: 700,
                                                        fontSize: 13,
                                                        color: '#fff',
                                                    }}
                                                >
                                                    {!other?.avatar && initial}
                                                </Avatar>
                                                <span style={{
                                                    position: 'absolute',
                                                    bottom: 0,
                                                    right: 0,
                                                    width: 9,
                                                    height: 9,
                                                    borderRadius: '50%',
                                                    background: '#10B981',
                                                    border: '2px solid var(--color-bg-layout)',
                                                }} />
                                            </div>

                                            {/* Name */}
                                            <span style={{
                                                color: isActive ? '#F1F5F9' : '#94A3B8',
                                                fontSize: 14,
                                                fontWeight: isActive ? 600 : 500,
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap',
                                                flex: 1,
                                                transition: 'color 0.15s',
                                            }}>
                        {displayName}
                      </span>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>
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
            </Sider>}

            {/* =========================================================
          Main Content Workspace
         ========================================================= */}
            <Layout style={{ marginLeft: isGroupPage ? 72 : 312, minWidth: 0, background: 'var(--color-bg-base)', minHeight: '100vh' }}>
                <Content style={{ position: 'relative', height: '100vh', overflowY: 'auto' }}>
                    <Outlet context={{
                        servers,
                        serversLoading,
                        serversError,
                        setServers,
                        onServerDeleted: handleServerDeleted,
                        openCreateModal: () => setIsCreateModalOpen(true),
                        openJoinModal: () => setIsJoinModalOpen(true),
                        openCreateChannelModal: (server) => {
                            setTargetChannelServer(server);
                            setIsCreateChannelModalOpen(true);
                        },
                        refreshServers: fetchServers
                    }} />
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

            {/* Join Server Modal */}
            <JoinServerModal
                open={isJoinModalOpen}
                onClose={() => setIsJoinModalOpen(false)}
                onSuccess={async (joinedServerId) => {
                    await fetchServers();
                    if (joinedServerId) {
                        navigate(`/servers/${joinedServerId}`);
                    }
                }}
            />

            {/* Create Channel Modal */}
            <CreateChannelModal
                open={isCreateChannelModalOpen}
                server={targetChannelServer}
                onClose={() => {
                    setIsCreateChannelModalOpen(false);
                    setTargetChannelServer(null);
                }}
                onSuccess={() => {
                    fetchServers();
                }}
            />
        </Layout>
    );
}
