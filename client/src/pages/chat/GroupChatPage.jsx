import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { Avatar, Button, Drawer, Grid, Skeleton, Spin } from 'antd';
import {
  ArrowLeftOutlined,
  MenuOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
  TeamOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import MessageInput from '../../components/chat/MessageInput';
import CreateChannelModal from '../../components/common/CreateChannelModal';
import EditChannelModal from '../../components/common/EditChannelModal';
import ServerInviteModal from '../../components/common/ServerInviteModal';
import ServerSettingsModal from '../../components/common/ServerSettingsModal';
import serverApi from '../../api/serverApi';
import channelApi from '../../api/channelApi';
import './group-chat.css';

function ChannelSidebar({
  server,
  user,
  channels,
  channelsLoading,
  channelsError,
  activeChannel,
  isOwner,
  onOpenInvite,
  onOpenSettings,
  onSelectChannel,
  onOpenCreateChannel,
  onOpenEditChannel,
  onRetryChannels,
}) {
  return (
    <div className="group-sidebar">
      <div className="group-sidebar__heading">
        <Avatar shape="square" src={server.iconUrl} size={36}>{server.name?.[0]?.toUpperCase()}</Avatar>
        <div className="group-sidebar__identity"><strong title={server.name}>{server.name}</strong><span>Không gian nhóm</span></div>
        {isOwner && <button type="button" className="group-sidebar-settings-btn" onClick={onOpenSettings}
          title="Cài đặt Server" aria-label="Cài đặt Server"><SettingOutlined /></button>}
      </div>
      <div className="group-sidebar__body">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
          <Link className="group-back-link" to="/"><ArrowLeftOutlined /> Tổng quan</Link>
          {isOwner && (
            <button
              type="button"
              className="group-sidebar-invite-btn"
              onClick={onOpenInvite}
              title="Mời thành viên vào Server"
              aria-label="Mời thành viên vào Server"
            >
              <UserAddOutlined /> <span>Mời</span>
            </button>
          )}
        </div>

        <div className="group-section-header">
          <h2 className="group-section-label">Kênh trò chuyện</h2>
          <button
            type="button"
            className="group-add-channel-btn"
            title="Tạo kênh mới"
            aria-label="Tạo kênh mới"
            onClick={onOpenCreateChannel}
          >
            <PlusOutlined />
          </button>
        </div>

        {channelsLoading ? (
          <div className="group-channel-loading" role="status" aria-label="Đang tải kênh">
            <Spin size="small" />
            <span>Đang tải kênh...</span>
          </div>
        ) : channelsError ? (
          <div className="group-channel-error" role="alert">
            <span>{channelsError}</span>
            <Button
              type="link"
              size="small"
              icon={<ReloadOutlined />}
              onClick={onRetryChannels}
              style={{ color: '#00f2fe', padding: 0, height: 'auto', textAlign: 'left' }}
            >
              Thử lại
            </Button>
          </div>
        ) : channels.length === 0 ? (
          <div className="group-channel-empty">
            <span className="group-channel-empty__hash" aria-hidden="true">#</span>
            <p>Chưa có kênh nào trong server.</p>
            <Button
              type="dashed"
              size="small"
              icon={<PlusOutlined />}
              onClick={onOpenCreateChannel}
              className="group-create-first-channel-btn"
            >
              Tạo kênh đầu tiên
            </Button>
          </div>
        ) : (
          <nav className="group-channels-nav" aria-label="Danh sách kênh">
            {channels.map((ch) => {
              const isActive = activeChannel?.id === ch.id;
              return (
                <div
                  key={ch.id}
                  className={`group-channel-row ${isActive ? 'group-channel-row--active' : ''}`}
                >
                  <button
                    type="button"
                    className={`group-channel-item ${isActive ? 'group-channel-item--active' : ''}`}
                    onClick={() => onSelectChannel(ch)}
                    aria-current={isActive ? 'page' : undefined}
                  >
                    <span className="group-channel-item__hash" aria-hidden="true">#</span>
                    <span className="group-channel-item__name" title={ch.name}>{ch.name}</span>
                    {isActive && <span className="group-channel-item__indicator" aria-hidden="true" />}
                  </button>
                  <button
                    type="button"
                    className="group-channel-edit-btn"
                    title={`Cài đặt kênh #${ch.name}`}
                    aria-label={`Cài đặt kênh #${ch.name}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenEditChannel?.(ch);
                    }}
                  >
                    <SettingOutlined />
                  </button>
                </div>
              );
            })}
          </nav>
        )}
      </div>
      <div className="group-user">
        <Avatar size={34}>{(user?.displayName || user?.username || 'U')[0].toUpperCase()}</Avatar>
        <div><strong>{user?.displayName || user?.username || 'Tài khoản của bạn'}</strong><span>Tài khoản của bạn</span></div>
        <Link to="/profile" className="group-icon-link" aria-label="Hồ sơ của bạn"><SettingOutlined /></Link>
      </div>
    </div>
  );
}

function MembersPanel({
  members,
  loading,
  error,
  onRetry,
  isOwner,
  onOpenInvite,
}) {
  return (
    <div className="group-members">
      <div className="group-members-header">
        <h2 className="group-section-label">
          Thành viên <span className="group-members-count">({members.length})</span>
        </h2>
        {isOwner && (
          <button
            type="button"
            className="group-members-invite-btn"
            onClick={onOpenInvite}
            title="Mời thành viên vào Server"
            aria-label="Mời thành viên vào Server"
          >
            <UserAddOutlined />
          </button>
        )}
      </div>

      {loading ? (
        <div className="group-members-loading" role="status" aria-label="Đang tải thành viên">
          <Spin size="small" />
          <span>Đang tải thành viên...</span>
        </div>
      ) : error ? (
        <div className="group-members-error" role="alert">
          <span>{error}</span>
          <Button
            type="link"
            size="small"
            icon={<ReloadOutlined />}
            onClick={onRetry}
            style={{ color: '#00f2fe', padding: 0, height: 'auto', textAlign: 'left' }}
          >
            Thử lại
          </Button>
        </div>
      ) : members.length === 0 ? (
        <div className="group-members__empty">
          <TeamOutlined aria-hidden="true" />
          <h3>Chưa có danh sách thành viên</h3>
          <p>Server hiện chưa có thành viên nào tham gia.</p>
        </div>
      ) : (
        <div className="group-members-list" role="list">
          {members.map((member) => {
            const displayName = member.nickname || member.name || member.username || 'Thành viên';
            const initial = displayName[0]?.toUpperCase() || 'U';
            const isOwner = member.role === 'OWNER';

            return (
              <div key={member.id} className="group-member-item" role="listitem">
                <Avatar
                  className="group-member-item__avatar"
                  size={32}
                  src={member.avatar}
                >
                  {initial}
                </Avatar>
                <div className="group-member-item__info">
                  <div className="group-member-item__name-row">
                    <span className="group-member-item__name" title={displayName}>
                      {displayName}
                    </span>
                    {isOwner && (
                      <span className="group-member-item__badge" title="Chủ nhóm">
                        Chủ nhóm
                      </span>
                    )}
                  </div>
                  {member.username && member.username !== displayName && (
                    <span className="group-member-item__username" title={`@${member.username}`}>
                      @{member.username}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function GroupChatPage() {
  const { serverId } = useParams();
  const { setServers, onServerDeleted } = useOutletContext();
  const navigate = useNavigate();
  const currentServerId = useRef(serverId);
  useEffect(() => {
    currentServerId.current = serverId;
    return () => { currentServerId.current = null; };
  }, [serverId]);
  const { currentUser } = useAuth();
  const screens = Grid.useBreakpoint();
  const [channelsOpen, setChannelsOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersVisible, setMembersVisible] = useState(true);
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [editingChannel, setEditingChannel] = useState(null);
  useEffect(() => { setIsSettingsOpen(false); setEditingChannel(null); }, [serverId]);
  const [detail, setDetail] = useState({ serverId, server: null, loading: true, error: null });
  const [reload, setReload] = useState(0);

  // Channels state
  const [channels, setChannels] = useState([]);
  const [channelsLoading, setChannelsLoading] = useState(true);
  const [channelsError, setChannelsError] = useState(null);
  const [activeChannel, setActiveChannel] = useState(null);
  const [isCreateChannelOpen, setIsCreateChannelOpen] = useState(false);

  // Members state
  const [members, setMembers] = useState([]);
  const [membersLoading, setMembersLoading] = useState(true);
  const [membersError, setMembersError] = useState(null);

  const fetchMembers = (id) => {
    setMembersLoading(true);
    setMembersError(null);
    serverApi.getMembers(id)
      .then((res) => {
        const list = res.data?.data || res.data || [];
        setMembers(list);
      })
      .catch((err) => {
        setMembersError(err.response?.data?.message || 'Không thể tải danh sách thành viên.');
      })
      .finally(() => {
        setMembersLoading(false);
      });
  };

  useEffect(() => {
    let active = true;
    if (serverId) {
      setMembersLoading(true);
      setMembersError(null);
      serverApi.getMembers(serverId)
        .then((res) => {
          if (!active) return;
          const list = res.data?.data || res.data || [];
          setMembers(list);
        })
        .catch((err) => {
          if (!active) return;
          setMembersError(err.response?.data?.message || 'Không thể tải danh sách thành viên.');
        })
        .finally(() => {
          if (active) setMembersLoading(false);
        });
    }
    return () => {
      active = false;
    };
  }, [serverId]);

  const fetchChannels = (id) => {
    setChannelsLoading(true);
    setChannelsError(null);
    channelApi.getByServerId(id)
      .then((res) => {
        const list = res.data?.data || res.data || [];
        setChannels(list);
        setActiveChannel((prev) => {
          if (prev && list.some((c) => c.id === prev.id)) {
            return prev;
          }
          return list.length > 0 ? list[0] : null;
        });
      })
      .catch((err) => {
        setChannelsError(err.response?.data?.message || 'Không thể tải danh sách kênh.');
      })
      .finally(() => {
        setChannelsLoading(false);
      });
  };

  useEffect(() => {
    if (serverId) {
      fetchChannels(serverId);
    }
  }, [serverId]);

  const handleSelectChannel = (channel) => {
    setActiveChannel(channel);
    if (!screens.lg) {
      setChannelsOpen(false);
    }
  };

  const handleChannelCreated = (newChannel) => {
    if (serverId) {
      fetchChannels(serverId);
    }
    if (newChannel) {
      setActiveChannel(newChannel);
    }
  };

  const handleChannelUpdated = (updatedChannel) => {
    if (!updatedChannel) return;
    setChannels((prevList) =>
      prevList.map((c) => (c.id === updatedChannel.id ? updatedChannel : c))
    );
    setActiveChannel((prev) =>
      prev?.id === updatedChannel.id ? updatedChannel : prev
    );
    if (serverId) {
      fetchChannels(serverId);
    }
  };

  useEffect(() => {
    let active = true;
    setDetail({ serverId, server: null, loading: true, error: null });
    serverApi.getById(serverId).then((response) => {
      const server = response.data?.data;
      if (!server || String(server.id) !== serverId || typeof server.name !== 'string') {
        throw new Error('Invalid server detail response');
      }
      if (active) setDetail({ serverId, server, loading: false, error: null });
    }).catch((error) => {
      if (!active) return;
      const status = error.response?.status;
      const message = status === 404 ? 'Nhóm này không tồn tại hoặc đã bị xóa.'
          : status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
            : status === 400 ? 'Đường dẫn nhóm không hợp lệ.'
              : 'Không thể tải thông tin nhóm. Vui lòng thử lại.';
      setDetail({ serverId, server: null, loading: false, error: { status, message } });
    });
    return () => { active = false; };
  }, [serverId, reload]);

  const server = detail.server;
  const isOwner = Boolean(
    server?.ownerId && currentUser?.id && String(server.ownerId) === String(currentUser.id)
  );

  if (detail.serverId !== serverId || detail.loading) {
    return (
      <div className="group-state" role="status" aria-label="Đang tải nhóm">
        <Spin size="large" />
        <p>Đang mở không gian nhóm…</p>
        <Skeleton active paragraph={{ rows: 3 }} title={false} />
      </div>
    );
  }

  if (detail.error?.status === 403) return <Navigate to="/" replace />;

  if (detail.error) {
    const title = detail.error.status === 404 ? 'Không tìm thấy nhóm của bạn' : 'Chưa tải được nhóm';
    return (
      <div className="group-state">
        <div className="group-state__icon"><TeamOutlined /></div>
        <h1>{title}</h1>
        <p role="alert">{detail.error.message}</p>
        <div className="group-state__actions">
          <Button onClick={() => setReload((value) => value + 1)}>Thử lại</Button>
          <Link to="/">Về tổng quan</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="group-workspace">
      <aside className="group-channel-column" aria-label="Kênh của nhóm">
        <ChannelSidebar
          server={server}
          user={currentUser}
          channels={channels}
          channelsLoading={channelsLoading}
          channelsError={channelsError}
          activeChannel={activeChannel}
          isOwner={isOwner}
          onOpenInvite={() => setIsInviteOpen(true)}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onSelectChannel={handleSelectChannel}
          onOpenCreateChannel={() => setIsCreateChannelOpen(true)}
          onOpenEditChannel={(ch) => setEditingChannel(ch)}
          onRetryChannels={() => fetchChannels(serverId)}
        />
      </aside>
      <main className="group-chat">
        <header className="group-chat__header">
          <Button className="group-mobile-channels" type="text" icon={<MenuOutlined />} aria-label="Mở danh sách kênh" onClick={() => setChannelsOpen(true)} />
          <span className="group-header-hash" aria-hidden="true">#</span>
          <div className="group-chat__title">
            <strong>{activeChannel ? activeChannel.name : 'Trò chuyện nhóm'}</strong>
            <span title={server.name}>{server.name}</span>
          </div>
          {activeChannel ? (
            <span className="group-channel-badge">#{activeChannel.name}</span>
          ) : (
            <span className="group-pending-label">Chưa chọn kênh</span>
          )}
          {activeChannel && (
            <Button
              type="text"
              className="group-header-edit-btn"
              icon={<SettingOutlined />}
              title={`Cài đặt kênh #${activeChannel.name}`}
              aria-label={`Cài đặt kênh #${activeChannel.name}`}
              onClick={() => setEditingChannel(activeChannel)}
            />
          )}
          {isOwner && (
            <Button
              type="text"
              className="group-header-invite-btn"
              icon={<UserAddOutlined />}
              title="Mời thành viên vào Server"
              aria-label="Mời thành viên vào Server"
              onClick={() => setIsInviteOpen(true)}
            />
          )}
          <Button
            type="text"
            icon={<TeamOutlined />}
            aria-label={screens.xl ? (membersVisible ? 'Ẩn danh sách thành viên' : 'Hiện danh sách thành viên') : 'Mở danh sách thành viên'}
            aria-expanded={screens.xl ? membersVisible : membersOpen}
            onClick={() => screens.xl ? setMembersVisible((visible) => !visible) : setMembersOpen(true)}
          />
        </header>
        <div className="group-chat__content">
          {activeChannel ? (
            <section className="group-welcome group-channel-welcome" aria-labelledby="channel-welcome-title">
              <div className="group-welcome__visual" aria-hidden="true">
                <div className="group-welcome__orbit" />
                <span className="group-channel-avatar-hash">#</span>
              </div>
              <span className="group-welcome__eyebrow">KÊNH TRÒ CHUYỆN</span>
              <h1 id="channel-welcome-title">Chào mừng đến với<br /><span>#{activeChannel.name}!</span></h1>
              <p>Đây là phần khởi đầu của kênh <strong>#{activeChannel.name}</strong> trong không gian <strong>{server.name}</strong>.</p>
            </section>
          ) : (
            <section className="group-welcome" aria-labelledby="group-welcome-title">
              <div className="group-welcome__visual" aria-hidden="true">
                <div className="group-welcome__orbit" />
                <Avatar shape="square" size={72} src={server.iconUrl}>{server.name?.[0]?.toUpperCase()}</Avatar>
                <span className="group-welcome__bubble"><MessageOutlined /></span>
              </div>
              <span className="group-welcome__eyebrow">KHÔNG GIAN CỦA BẠN</span>
              <h1 id="group-welcome-title">Chào mừng đến với<br /><span>{server.name}</span></h1>
              <p>Nơi cả nhóm cùng trao đổi, chia sẻ ý tưởng<br className="group-desktop-break" /> và giữ kết nối mỗi ngày.</p>
              <div className="group-welcome__notice">
                <span aria-hidden="true">#</span>
                <div>
                  <strong>Cuộc trò chuyện bắt đầu từ một kênh</strong>
                  <p>Hãy chọn một kênh ở cột bên trái hoặc tạo kênh mới để bắt đầu thảo luận.</p>
                </div>
              </div>
            </section>
          )}
        </div>
        <MessageInput
          key={`${serverId}-${activeChannel?.id || 'none'}`}
          channelId={activeChannel?.id}
          channelName={activeChannel?.name}
          disabled={!activeChannel}
        />
      </main>
      {screens.xl && membersVisible && (
        <aside className="group-member-column" aria-label="Thành viên của nhóm">
          <MembersPanel
            members={members}
            loading={membersLoading}
            error={membersError}
            isOwner={isOwner}
            onOpenInvite={() => setIsInviteOpen(true)}
            onRetry={() => fetchMembers(serverId)}
          />
        </aside>
      )}
      <Drawer
        title="Kênh của nhóm"
        placement="left"
        width="min(320px, 100vw)"
        open={channelsOpen && !screens.lg}
        onClose={() => setChannelsOpen(false)}
        styles={{ body: { padding: 0 } }}
      >
        <ChannelSidebar
          server={server}
          user={currentUser}
          channels={channels}
          channelsLoading={channelsLoading}
          channelsError={channelsError}
          activeChannel={activeChannel}
          isOwner={isOwner}
          onOpenInvite={() => {
            setChannelsOpen(false);
            setIsInviteOpen(true);
          }}
          onOpenSettings={() => {
            setChannelsOpen(false);
            setIsSettingsOpen(true);
          }}
          onSelectChannel={handleSelectChannel}
          onOpenCreateChannel={() => {
            setChannelsOpen(false);
            setIsCreateChannelOpen(true);
          }}
          onOpenEditChannel={(ch) => {
            setChannelsOpen(false);
            setEditingChannel(ch);
          }}
          onRetryChannels={() => fetchChannels(serverId)}
        />
      </Drawer>
      <Drawer
        title="Thành viên của nhóm"
        width="min(320px, 100vw)"
        open={membersOpen && !screens.xl}
        onClose={() => setMembersOpen(false)}
        styles={{ body: { padding: 0 } }}
      >
        <MembersPanel
          members={members}
          loading={membersLoading}
          error={membersError}
          isOwner={isOwner}
          onOpenInvite={() => {
            setMembersOpen(false);
            setIsInviteOpen(true);
          }}
          onRetry={() => fetchMembers(serverId)}
        />
      </Drawer>

      <CreateChannelModal
        open={isCreateChannelOpen}
        onClose={() => setIsCreateChannelOpen(false)}
        server={server}
        onSuccess={handleChannelCreated}
      />

      <EditChannelModal
        open={Boolean(editingChannel)}
        channel={editingChannel}
        server={server}
        onClose={() => setEditingChannel(null)}
        onSuccess={handleChannelUpdated}
      />

      <ServerInviteModal
        open={isInviteOpen}
        onClose={() => setIsInviteOpen(false)}
        server={server}
      />
      <ServerSettingsModal key={serverId} open={isSettingsOpen && isOwner} server={server}
        onDeleted={id => {
          onServerDeleted(id);
          if (String(currentServerId.current) === String(id)) {
            setIsSettingsOpen(false);
            navigate('/', { replace: true });
          }
        }}
        onClose={() => setIsSettingsOpen(false)} onSaved={updated => {
          setDetail(previous => ({ ...previous, server: updated }));
          setServers(previous => previous.map(item => String(item.id) === String(updated.id) ? updated : item));
        }} />
    </div>
  );
}
