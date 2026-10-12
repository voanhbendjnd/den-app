import { useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';
import { Avatar, Button, Drawer, Grid, Skeleton, Spin } from 'antd';
import { ArrowLeftOutlined, MenuOutlined, MessageOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import MessageInput from '../../components/chat/MessageInput';
import './group-chat.css';

function ChannelSidebar({ server, user }) {
  return (
    <div className="group-sidebar">
      <div className="group-sidebar__heading">
        <Avatar shape="square" src={server.iconUrl} size={36}>{server.name?.[0]?.toUpperCase()}</Avatar>
        <div className="group-sidebar__identity"><strong title={server.name}>{server.name}</strong><span>Không gian nhóm</span></div>
      </div>
      <div className="group-sidebar__body">
        <Link className="group-back-link" to="/"><ArrowLeftOutlined /> Tổng quan</Link>
        <h2 className="group-section-label">Kênh trò chuyện</h2>
        <div className="group-channel-empty">
          <span className="group-channel-empty__hash" aria-hidden="true">#</span>
          <p>Danh sách kênh chưa sẵn sàng.</p>
          <span>Các kênh của nhóm sẽ xuất hiện tại đây.</span>
        </div>
      </div>
      <div className="group-user">
        <Avatar size={34}>{(user?.displayName || user?.username || 'U')[0].toUpperCase()}</Avatar>
        <div><strong>{user?.displayName || user?.username || 'Tài khoản của bạn'}</strong><span>Tài khoản của bạn</span></div>
        <Link to="/profile" className="group-icon-link" aria-label="Hồ sơ của bạn"><SettingOutlined /></Link>
      </div>
    </div>
  );
}

function MembersPanel() {
  return (
    <div className="group-members">
      <h2 className="group-section-label">Thành viên</h2>
      <div className="group-members__empty">
        <TeamOutlined aria-hidden="true" />
        <h3>Chưa có danh sách thành viên</h3>
        <p>Thông tin thành viên của nhóm sẽ hiển thị tại đây khi sẵn sàng.</p>
      </div>
    </div>
  );
}

export default function GroupChatPage() {
  const { serverId } = useParams();
  const { servers, serversLoading, serversError, refreshServers } = useOutletContext();
  const { currentUser } = useAuth();
  const screens = Grid.useBreakpoint();
  const [channelsOpen, setChannelsOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersVisible, setMembersVisible] = useState(true);
  const server = servers.find((item) => String(item.id) === serverId);

  if (serversLoading) {
    return (
      <div className="group-state" role="status" aria-label="Đang tải nhóm">
        <Spin size="large" />
        <p>Đang mở không gian nhóm…</p>
        <Skeleton active paragraph={{ rows: 3 }} title={false} />
      </div>
    );
  }

  if (serversError || !server) {
    return (
      <div className="group-state">
        <div className="group-state__icon"><TeamOutlined /></div>
        <h1>{serversError ? 'Chưa tải được nhóm' : 'Không tìm thấy nhóm của bạn'}</h1>
        <p role={serversError ? 'alert' : undefined}>{serversError || 'Nhóm này không nằm trong danh sách nhóm bạn đang tham gia.'}</p>
        <div className="group-state__actions">
          <Button onClick={refreshServers}>Thử lại</Button>
          <Link to="/">Về tổng quan</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="group-workspace">
      <aside className="group-channel-column" aria-label="Kênh của nhóm">
        <ChannelSidebar server={server} user={currentUser} />
      </aside>
      <main className="group-chat">
        <header className="group-chat__header">
          <Button className="group-mobile-channels" type="text" icon={<MenuOutlined />} aria-label="Mở danh sách kênh" onClick={() => setChannelsOpen(true)} />
          <span className="group-header-hash" aria-hidden="true">#</span>
          <div className="group-chat__title"><strong>Trò chuyện nhóm</strong><span title={server.name}>{server.name}</span></div>
          <span className="group-pending-label">Chưa chọn kênh</span>
          <Button
            type="text"
            icon={<TeamOutlined />}
            aria-label={screens.xl ? (membersVisible ? 'Ẩn danh sách thành viên' : 'Hiện danh sách thành viên') : 'Mở danh sách thành viên'}
            aria-expanded={screens.xl ? membersVisible : membersOpen}
            onClick={() => screens.xl ? setMembersVisible((visible) => !visible) : setMembersOpen(true)}
          />
        </header>
        <div className="group-chat__content">
          <section className="group-welcome" aria-labelledby="group-welcome-title">
            <div className="group-welcome__visual" aria-hidden="true">
              <div className="group-welcome__orbit" />
              <Avatar shape="square" size={72} src={server.iconUrl}>{server.name?.[0]?.toUpperCase()}</Avatar>
              <span className="group-welcome__bubble"><MessageOutlined /></span>
            </div>
            <span className="group-welcome__eyebrow">KHÔNG GIAN CỦA BẠN</span>
            <h1 id="group-welcome-title">Chào mừng đến với<br /><span>{server.name}</span></h1>
            <p>Nơi cả nhóm cùng trao đổi, chia sẻ ý tưởng<br className="group-desktop-break" /> và giữ kết nối mỗi ngày.</p>
            <div className="group-welcome__notice"><span aria-hidden="true">#</span><div><strong>Cuộc trò chuyện bắt đầu từ một kênh</strong><p>Danh sách kênh chưa sẵn sàng. Bạn có thể quay lại tổng quan hoặc chuyển sang nhóm khác.</p></div></div>
            <Link className="group-back-link" to="/"><ArrowLeftOutlined /> Về tổng quan</Link>
          </section>
        </div>
        <MessageInput key={serverId} disabled />
      </main>
      {screens.xl && membersVisible && <aside className="group-member-column" aria-label="Thành viên của nhóm"><MembersPanel /></aside>}
      <Drawer
        title="Kênh của nhóm"
        placement="left"
        width="min(320px, 100vw)"
        open={channelsOpen && !screens.lg}
        onClose={() => setChannelsOpen(false)}
        styles={{ body: { padding: 0 } }}
      >
        <ChannelSidebar server={server} user={currentUser} />
      </Drawer>
      <Drawer
        title="Thành viên của nhóm"
        width="min(320px, 100vw)"
        open={membersOpen && !screens.xl}
        onClose={() => setMembersOpen(false)}
        styles={{ body: { padding: 0 } }}
      >
        <MembersPanel />
      </Drawer>
    </div>
  );
}
