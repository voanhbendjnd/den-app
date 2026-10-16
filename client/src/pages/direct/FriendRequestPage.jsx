import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Button, Typography, Tag, Empty, Spin, Input, Popconfirm, message } from 'antd';
import {
  UserAddOutlined,
  ClockCircleOutlined,
  TeamOutlined,
  ReloadOutlined,
  CheckOutlined,
  CloseOutlined,
  MessageOutlined,
  SearchOutlined,
  UsergroupAddOutlined,
  CheckCircleOutlined,
  UserDeleteOutlined,
  StopOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/vi';
import { useFriendRequests } from '../../hooks/useFriendRequests';
import { useFriends } from '../../hooks/useFriends';
import { useBlockedUsers } from '../../hooks/useBlockedUsers';
import conversationApi from '../../api/conversationApi';

dayjs.extend(relativeTime);
dayjs.locale('vi');

const { Text, Title } = Typography;

// ─── Friend Card (Đã là bạn bè) ────────────────────────────────────────────────
function FriendCard({ friend, onStartChat, chatLoading, onUnfriend, unfriendLoading, onBlock, blockLoading }) {
  const isChatting = !!chatLoading[friend.senderId];
  const isUnfriending = !!unfriendLoading?.[friend.senderId];
  const isBlocking = !!blockLoading?.[friend.senderId];

  return (
    <div
      id={`friend-card-${friend.senderId}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: '14px 20px',
        borderRadius: 14,
        background: 'rgba(255,255,255,0.03)',
        border: '1px solid rgba(255,255,255,0.06)',
        transition: 'all 0.25s cubic-bezier(0.16,1,0.3,1)',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(99,102,241,0.35)';
        e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
        e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.2)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
        e.currentTarget.style.background = 'rgba(255,255,255,0.03)';
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      {/* Avatar */}
      <div style={{ position: 'relative', flexShrink: 0 }}>
        <Avatar
          size={50}
          src={friend.avatar}
          style={{
            background: 'linear-gradient(135deg, #10B981, #06B6D4)',
            fontSize: 20,
            fontWeight: 700,
            color: '#fff',
            boxShadow: '0 0 12px rgba(16,185,129,0.25)',
          }}
        >
          {!friend.avatar && (friend.name || friend.username)?.[0]?.toUpperCase()}
        </Avatar>
      </div>

      {/* Info */}
      <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
          <Text
            style={{ color: '#F1F5F9', fontWeight: 700, fontSize: 15 }}
            ellipsis
          >
            {friend.name || friend.username}
          </Text>
          <Tag
            icon={<CheckCircleOutlined />}
            style={{
              borderRadius: 12,
              fontSize: 11,
              padding: '0 8px',
              lineHeight: '18px',
              fontWeight: 600,
              background: 'rgba(16,185,129,0.12)',
              borderColor: 'rgba(16,185,129,0.3)',
              color: '#34D399',
              flexShrink: 0,
            }}
          >
            Bạn bè
          </Tag>
        </div>
        <Text style={{ color: '#64748B', fontSize: 12 }}>@{friend.username}</Text>
      </div>

      {/* Actions: Nhắn tin & Hủy kết bạn */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <Button
          id={`btn-chat-${friend.senderId}`}
          type="primary"
          icon={<MessageOutlined />}
          loading={isChatting}
          disabled={isUnfriending}
          onClick={() => onStartChat(friend)}
          style={{
            background: 'linear-gradient(135deg, #4F46E5, #6366F1)',
            border: 'none',
            borderRadius: 8,
            fontWeight: 600,
            fontSize: 13,
            height: 36,
            padding: '0 16px',
            boxShadow: '0 2px 10px rgba(99,102,241,0.25)',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          Nhắn tin
        </Button>

        <Popconfirm
          title="Hủy kết bạn"
          description={`Bạn có chắc chắn muốn hủy kết bạn với ${friend.name || friend.username}?`}
          okText="Hủy kết bạn"
          cancelText="Không"
          okButtonProps={{ danger: true }}
          onConfirm={() => onUnfriend(friend.senderId)}
        >
          <Button
            id={`btn-unfriend-${friend.senderId}`}
            icon={<UserDeleteOutlined />}
            loading={isUnfriending}
            disabled={isChatting || isBlocking}
            style={{
              background: 'rgba(255, 255, 255, 0.05)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              color: '#94A3B8',
              borderRadius: 8,
              fontWeight: 600,
              fontSize: 13,
              height: 36,
              padding: '0 12px',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = 'rgba(239, 68, 68, 0.5)';
              e.currentTarget.style.color = '#F87171';
              e.currentTarget.style.background = 'rgba(239, 68, 68, 0.08)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.12)';
              e.currentTarget.style.color = '#94A3B8';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
            }}
          >
            Hủy kết bạn
          </Button>
        </Popconfirm>

        <Popconfirm
          title="Chặn người dùng"
          description={`Bạn có chắc muốn chặn ${friend.name || friend.username}? Hai người sẽ không còn là bạn bè và người này sẽ không tìm thấy bạn qua tìm kiếm.`}
          okText="Chặn"
          cancelText="Không"
          okButtonProps={{ danger: true }}
          onConfirm={() => onBlock(friend.senderId)}
        >
          <Button
            id={`btn-block-${friend.senderId}`}
            icon={<StopOutlined />}
            loading={isBlocking}
            disabled={isChatting || isUnfriending}
            style={{
              background: 'rgba(239, 68, 68, 0.08)',
              border: '1px solid rgba(239, 68, 68, 0.25)',
              color: '#F87171',
              borderRadius: 8,
              fontWeight: 600,
              fontSize: 13,
              height: 36,
              padding: '0 12px',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#EF4444';
              e.currentTarget.style.background = 'rgba(239, 68, 68, 0.18)';
              e.currentTarget.style.boxShadow = '0 0 10px rgba(239, 68, 68, 0.3)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = 'rgba(239, 68, 68, 0.25)';
              e.currentTarget.style.background = 'rgba(239, 68, 68, 0.08)';
              e.currentTarget.style.boxShadow = 'none';
            }}
          >
            Chặn
          </Button>
        </Popconfirm>
      </div>
    </div>
  );
}

// ─── Blocked User Card (Đã bị chặn) ──────────────────────────────────────────
function BlockedUserCard({ user, onUnblock, unblockLoading }) {
  const isUnblocking = !!unblockLoading?.[user.senderId];

  return (
    <div
      id={`blocked-card-${user.senderId}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: '14px 20px',
        borderRadius: 14,
        background: 'rgba(255,255,255,0.03)',
        border: '1px solid rgba(255,255,255,0.06)',
        transition: 'all 0.25s cubic-bezier(0.16,1,0.3,1)',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(239,68,68,0.35)';
        e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
        e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.2)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
        e.currentTarget.style.background = 'rgba(255,255,255,0.03)';
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      {/* Avatar */}
      <div style={{ position: 'relative', flexShrink: 0 }}>
        <Avatar
          size={50}
          src={user.avatar}
          style={{
            background: 'linear-gradient(135deg, #EF4444, #F59E0B)',
            fontSize: 20,
            fontWeight: 700,
            color: '#fff',
            boxShadow: '0 0 12px rgba(239,68,68,0.25)',
          }}
        >
          {!user.avatar && (user.name || user.username)?.[0]?.toUpperCase()}
        </Avatar>
      </div>

      {/* Info */}
      <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
          <Text
            style={{ color: '#F1F5F9', fontWeight: 700, fontSize: 15 }}
            ellipsis
          >
            {user.name || user.username}
          </Text>
          <Tag
            icon={<StopOutlined />}
            style={{
              borderRadius: 12,
              fontSize: 11,
              padding: '0 8px',
              lineHeight: '18px',
              fontWeight: 600,
              background: 'rgba(239,68,68,0.12)',
              borderColor: 'rgba(239,68,68,0.3)',
              color: '#F87171',
              flexShrink: 0,
            }}
          >
            Đã chặn
          </Tag>
        </div>
        <Text style={{ color: '#64748B', fontSize: 12 }}>@{user.username}</Text>
      </div>

      {/* Actions: Bỏ chặn */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <Popconfirm
          title="Bỏ chặn người dùng"
          description={`Bạn có chắc muốn bỏ chặn ${user.name || user.username}? Người này sẽ có thể tìm thấy bạn và gửi lời mời kết bạn.`}
          okText="Bỏ chặn"
          cancelText="Không"
          onConfirm={() => onUnblock(user.senderId)}
        >
          <Button
            id={`btn-unblock-${user.senderId}`}
            icon={<UnlockOutlined />}
            loading={isUnblocking}
            style={{
              background: 'rgba(99,102,241,0.12)',
              border: '1px solid rgba(99,102,241,0.3)',
              color: '#A5B4FC',
              borderRadius: 8,
              fontWeight: 600,
              fontSize: 13,
              height: 36,
              padding: '0 14px',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#6366F1';
              e.currentTarget.style.background = 'rgba(99,102,241,0.25)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = 'rgba(99,102,241,0.3)';
              e.currentTarget.style.background = 'rgba(99,102,241,0.12)';
            }}
          >
            Bỏ chặn
          </Button>
        </Popconfirm>
      </div>
    </div>
  );
}

// ─── Friend Request Card (Chờ xử lý) ───────────────────────────────────────────
function FriendRequestCard({ req, isNew, onAccept, onRemove, actionStatus }) {
  const isAccepting = actionStatus === 'accepting';
  const isRemoving = actionStatus === 'removing';
  const isActionDisabled = !!actionStatus;

  return (
    <div
      id={`friend-req-${req.senderId}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: '16px 20px',
        borderRadius: 14,
        background: isNew
          ? 'rgba(99,102,241,0.12)'
          : 'rgba(255,255,255,0.03)',
        border: isNew
          ? '1px solid rgba(99,102,241,0.45)'
          : '1px solid rgba(255,255,255,0.06)',
        transition: 'all 0.4s cubic-bezier(0.16,1,0.3,1)',
        boxShadow: isNew ? '0 0 20px rgba(99,102,241,0.2)' : 'none',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Shimmer dải sáng khi mới */}
      {isNew && (
        <div
          style={{
            position: 'absolute',
            top: 0, left: '-100%',
            width: '60%', height: '100%',
            background: 'linear-gradient(90deg, transparent, rgba(99,102,241,0.15), transparent)',
            animation: 'friendCardShimmer 1.2s ease-in-out',
            pointerEvents: 'none',
          }}
        />
      )}

      {/* Avatar */}
      <div style={{ position: 'relative', flexShrink: 0 }}>
        <Avatar
          size={52}
          src={req.avatar}
          style={{
            background: 'linear-gradient(135deg, #6366F1, #00F2FE)',
            fontSize: 20,
            fontWeight: 700,
            color: '#fff',
          }}
        >
          {!req.avatar && (req.name || req.username)?.[0]?.toUpperCase()}
        </Avatar>
        {isNew && (
          <span
            style={{
              position: 'absolute',
              top: -2, right: -2,
              width: 14, height: 14,
              borderRadius: '50%',
              background: '#6366F1',
              border: '2px solid #080B11',
              boxShadow: '0 0 8px #6366F1',
              animation: 'pulse 1.5s infinite',
            }}
          />
        )}
      </div>

      {/* Info */}
      <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <Text
            style={{ color: '#F1F5F9', fontWeight: 700, fontSize: 15 }}
            ellipsis
          >
            {req.name || req.username}
          </Text>
          {isNew && (
            <Tag
              color="geekblue"
              style={{
                borderRadius: 20,
                fontSize: 11,
                padding: '0 8px',
                lineHeight: '20px',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              Mới
            </Tag>
          )}
        </div>
        <Text style={{ color: '#64748B', fontSize: 12 }}>@{req.username}</Text>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            marginTop: 4,
          }}
        >
          <ClockCircleOutlined style={{ fontSize: 11, color: '#475569' }} />
          <Text style={{ color: '#475569', fontSize: 11 }}>
            {dayjs(req.lastModifiedDate).fromNow()}
          </Text>
        </div>
      </div>

      {/* Action Buttons: Đồng ý / Từ chối */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        {/* Nút Đồng ý */}
        <Button
          id={`btn-accept-${req.senderId}`}
          type="primary"
          icon={<CheckOutlined />}
          loading={isAccepting}
          disabled={isActionDisabled}
          onClick={() => onAccept(req.senderId)}
          style={{
            background: 'linear-gradient(135deg, #4F46E5, #06B6D4)',
            border: 'none',
            borderRadius: 8,
            fontWeight: 600,
            fontSize: 13,
            height: 36,
            padding: '0 16px',
            boxShadow: '0 2px 10px rgba(79, 70, 229, 0.3)',
          }}
        >
          Đồng ý
        </Button>

        {/* Nút Từ chối */}
        <Button
          id={`btn-remove-${req.senderId}`}
          icon={<CloseOutlined />}
          loading={isRemoving}
          disabled={isActionDisabled}
          onClick={() => onRemove(req.senderId)}
          style={{
            background: 'rgba(255, 255, 255, 0.05)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            color: '#94A3B8',
            borderRadius: 8,
            fontWeight: 600,
            fontSize: 13,
            height: 36,
            padding: '0 14px',
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.borderColor = 'rgba(239, 68, 68, 0.5)';
            e.currentTarget.style.color = '#F87171';
            e.currentTarget.style.background = 'rgba(239, 68, 68, 0.08)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.12)';
            e.currentTarget.style.color = '#94A3B8';
            e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
          }}
        >
          Từ chối
        </Button>
      </div>
    </div>
  );
}

// ─── Main Friends Page ────────────────────────────────────────────────────────
export default function FriendRequestPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('all'); // 'all' | 'pending'
  const [chatLoading, setChatLoading] = useState({});

  // Hook Lời mời kết bạn
  const {
    requests,
    loading: requestsLoading,
    hasMore: requestsHasMore,
    loadMore: requestsLoadMore,
    newRequestIds,
    actionLoading,
    acceptRequest,
    removeRequest,
  } = useFriendRequests();

  // Hook Tất cả bạn bè
  const {
    friends,
    filteredFriends,
    loading: friendsLoading,
    hasMore: friendsHasMore,
    loadMore: friendsLoadMore,
    refresh: refreshFriends,
    searchQuery,
    setSearchQuery,
    unfriend,
    unfriendLoading,
    blockUser,
    blockLoading,
  } = useFriends();

  // Hook Người dùng đã chặn
  const {
    blockedUsers,
    loading: blockedLoading,
    hasMore: blockedHasMore,
    loadMore: blockedLoadMore,
    refresh: refreshBlocked,
    unblockUser,
    unblockLoading,
  } = useBlockedUsers();

  // Xử lý Chặn xong thì refresh lại danh sách đã chặn
  const handleBlockAndRefresh = async (friendId) => {
    await blockUser(friendId);
    refreshBlocked();
  };

  // Xử lý Accept xong thì refresh lại danh sách bạn bè
  const handleAcceptAndRefresh = async (senderId) => {
    await acceptRequest(senderId);
    refreshFriends();
  };

  // Mở cuộc trò chuyện 1-1 với bạn
  const handleStartChat = async (friend) => {
    const friendId = friend.senderId;
    setChatLoading((prev) => ({ ...prev, [friendId]: true }));
    try {
      const convRes = await conversationApi.getAll();
      const conversations = convRes.data?.data ?? convRes.data ?? [];
      const existing = conversations.find(
        (c) => c.otherUser?.userId === friendId
      );

      if (existing) {
        navigate(`/direct/${existing.conversationId}`, {
          state: { otherUser: existing.otherUser },
        });
      } else {
        navigate(`/direct/new`, {
          state: {
            targetUser: {
              id: friendId,
              name: friend.name,
              username: friend.username,
              avatar: friend.avatar,
            },
          },
        });
      }
    } catch {
      message.error('Không thể mở hội thoại.');
    } finally {
      setChatLoading((prev) => ({ ...prev, [friendId]: false }));
    }
  };

  return (
    <div
      id="friends-page"
      style={{
        minHeight: '100vh',
        background: '#080B11',
        padding: '32px 24px',
        maxWidth: 780,
        margin: '0 auto',
      }}
    >
      {/* Keyframes inline */}
      <style>{`
        @keyframes friendCardShimmer {
          0%   { left: -100%; }
          100% { left: 200%; }
        }
        @keyframes pulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50%       { opacity: 0.6; transform: scale(1.3); }
        }
        @keyframes slideInDown {
          from { opacity: 0; transform: translateY(-14px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .friend-req-new-enter {
          animation: slideInDown 0.35s cubic-bezier(0.16,1,0.3,1);
        }
      `}</style>

      {/* ── Header ── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 24,
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: 'linear-gradient(135deg, rgba(99,102,241,0.25), rgba(0,242,254,0.15))',
              border: '1px solid rgba(99,102,241,0.35)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <TeamOutlined style={{ fontSize: 22, color: '#818CF8' }} />
          </div>
          <div>
            <Title
              level={3}
              style={{ color: '#F1F5F9', margin: 0, fontWeight: 800, letterSpacing: '-0.3px' }}
            >
              Bạn bè
            </Title>
            <Text style={{ color: '#475569', fontSize: 13 }}>
              Quản lý bạn bè và các lời mời kết bạn của bạn
            </Text>
          </div>
        </div>
      </div>

      {/* ── Discord Style Navigation Tabs ── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 20,
          borderBottom: '1px solid rgba(255,255,255,0.06)',
          paddingBottom: 14,
        }}
      >
        {/* Tab Tất cả bạn bè */}
        <button
          id="tab-all-friends"
          type="button"
          onClick={() => setActiveTab('all')}
          style={{
            background: activeTab === 'all' ? 'rgba(99,102,241,0.18)' : 'transparent',
            border: activeTab === 'all' ? '1px solid rgba(99,102,241,0.4)' : '1px solid transparent',
            color: activeTab === 'all' ? '#FFFFFF' : '#94A3B8',
            borderRadius: 10,
            padding: '7px 16px',
            fontSize: 14,
            fontWeight: 700,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            transition: 'all 0.2s',
          }}
        >
          <UsergroupAddOutlined />
          <span>Tất cả</span>
          {friends.length > 0 && (
            <span
              style={{
                background: activeTab === 'all' ? '#6366F1' : 'rgba(255,255,255,0.1)',
                color: '#fff',
                fontSize: 11,
                padding: '1px 7px',
                borderRadius: 10,
                fontWeight: 800,
              }}
            >
              {friends.length}
            </span>
          )}
        </button>

        {/* Tab Chờ xử lý (Lời mời nhận được) */}
        <button
          id="tab-pending-friends"
          type="button"
          onClick={() => setActiveTab('pending')}
          style={{
            background: activeTab === 'pending' ? 'rgba(99,102,241,0.18)' : 'transparent',
            border: activeTab === 'pending' ? '1px solid rgba(99,102,241,0.4)' : '1px solid transparent',
            color: activeTab === 'pending' ? '#FFFFFF' : '#94A3B8',
            borderRadius: 10,
            padding: '7px 16px',
            fontSize: 14,
            fontWeight: 700,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            transition: 'all 0.2s',
          }}
        >
          <UserAddOutlined />
          <span>Chờ xử lý</span>
          {requests.length > 0 && (
            <span
              style={{
                background: 'linear-gradient(135deg, #6366F1, #00F2FE)',
                color: '#fff',
                fontSize: 11,
                padding: '1px 7px',
                borderRadius: 10,
                fontWeight: 800,
              }}
            >
              {requests.length}
            </span>
          )}
        </button>

        {/* Tab Đã chặn */}
        <button
          id="tab-blocked-friends"
          type="button"
          onClick={() => setActiveTab('blocked')}
          style={{
            background: activeTab === 'blocked' ? 'rgba(99,102,241,0.18)' : 'transparent',
            border: activeTab === 'blocked' ? '1px solid rgba(99,102,241,0.4)' : '1px solid transparent',
            color: activeTab === 'blocked' ? '#FFFFFF' : '#94A3B8',
            borderRadius: 10,
            padding: '7px 16px',
            fontSize: 14,
            fontWeight: 700,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            transition: 'all 0.2s',
          }}
        >
          <StopOutlined />
          <span>Đã chặn</span>
          {blockedUsers.length > 0 && (
            <span
              style={{
                background: 'rgba(239, 68, 68, 0.8)',
                color: '#fff',
                fontSize: 11,
                padding: '1px 7px',
                borderRadius: 10,
                fontWeight: 800,
              }}
            >
              {blockedUsers.length}
            </span>
          )}
        </button>
      </div>

      {/* ── TAB 1: TẤT CẢ BẠN BÈ ── */}
      {activeTab === 'all' && (
        <div>
          {/* Ô tìm kiếm bạn bè */}
          <div style={{ marginBottom: 18 }}>
            <Input
              id="input-search-friends"
              prefix={<SearchOutlined style={{ color: '#64748B' }} />}
              placeholder="Tìm kiếm theo tên hoặc @username..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              allowClear
              style={{
                background: '#0F1422',
                borderColor: 'rgba(255,255,255,0.08)',
                borderRadius: 10,
                height: 42,
                color: '#F1F5F9',
                fontSize: 14,
              }}
            />
          </div>

          {/* Loading */}
          {friendsLoading && friends.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: 80,
                gap: 16,
              }}
            >
              <Spin size="large" />
              <Text style={{ color: '#64748B' }}>Đang tải danh sách bạn bè...</Text>
            </div>
          ) : filteredFriends.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: 80,
                gap: 16,
              }}
            >
              <div style={{ fontSize: 56, lineHeight: 1 }}>👥</div>
              <Title level={4} style={{ color: '#64748B', margin: 0, fontWeight: 700 }}>
                {searchQuery ? 'Không tìm thấy bạn bè phù hợp' : 'Chưa có bạn bè nào'}
              </Title>
              <Text style={{ color: '#475569', textAlign: 'center', maxWidth: 320 }}>
                {searchQuery
                  ? 'Thử tìm kiếm với từ khóa khác.'
                  : 'Hãy mở panel Tìm kiếm người dùng ở thanh bên để kết nối và kết bạn với mọi người!'}
              </Text>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {filteredFriends.map((f) => (
                <FriendCard
                  key={f.senderId}
                  friend={f}
                  onStartChat={handleStartChat}
                  chatLoading={chatLoading}
                  onUnfriend={unfriend}
                  unfriendLoading={unfriendLoading}
                  onBlock={handleBlockAndRefresh}
                  blockLoading={blockLoading}
                />
              ))}

              {/* Load more */}
              {friendsHasMore && (
                <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                  <Button
                    id="btn-load-more-friends"
                    icon={friendsLoading ? <Spin size="small" /> : <ReloadOutlined />}
                    onClick={friendsLoadMore}
                    disabled={friendsLoading}
                    style={{
                      background: 'rgba(99,102,241,0.1)',
                      borderColor: 'rgba(99,102,241,0.3)',
                      color: '#A5B4FC',
                      borderRadius: 20,
                      padding: '0 20px',
                      height: 36,
                    }}
                  >
                    {friendsLoading ? 'Đang tải...' : 'Tải thêm'}
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── TAB 2: LỜI MỜI KẾT BẠN (CHỜ XỬ LÝ) ── */}
      {activeTab === 'pending' && (
        <div>
          {requestsLoading && requests.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: 80,
                gap: 16,
              }}
            >
              <Spin size="large" />
              <Text style={{ color: '#64748B' }}>Đang tải lời mời kết bạn...</Text>
            </div>
          ) : requests.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: 80,
                gap: 16,
              }}
            >
              <div style={{ fontSize: 56, lineHeight: 1 }}>🤝</div>
              <Title level={4} style={{ color: '#64748B', margin: 0, fontWeight: 700 }}>
                Chưa có lời mời nào
              </Title>
              <Text style={{ color: '#475569', textAlign: 'center', maxWidth: 320 }}>
                Khi ai đó gửi lời mời kết bạn tới bạn, lời mời sẽ xuất hiện ở đây theo thời gian thực.
              </Text>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {requests.map((req) => (
                <div
                  key={req.senderId}
                  className={newRequestIds.has(req.senderId) ? 'friend-req-new-enter' : ''}
                >
                  <FriendRequestCard
                    req={req}
                    isNew={newRequestIds.has(req.senderId)}
                    onAccept={handleAcceptAndRefresh}
                    onRemove={removeRequest}
                    actionStatus={actionLoading[req.senderId]}
                  />
                </div>
              ))}

              {/* Load more */}
              {requestsHasMore && (
                <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                  <Button
                    id="btn-load-more-friend-requests"
                    icon={requestsLoading ? <Spin size="small" /> : <ReloadOutlined />}
                    onClick={requestsLoadMore}
                    disabled={requestsLoading}
                    style={{
                      background: 'rgba(99,102,241,0.1)',
                      borderColor: 'rgba(99,102,241,0.3)',
                      color: '#A5B4FC',
                      borderRadius: 20,
                      padding: '0 20px',
                      height: 36,
                    }}
                  >
                    {requestsLoading ? 'Đang tải...' : 'Tải thêm'}
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── TAB 3: ĐÃ CHẶN ── */}
      {activeTab === 'blocked' && (
        <div>
          {blockedLoading && blockedUsers.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: 80,
                gap: 16,
              }}
            >
              <Spin size="large" />
              <Text style={{ color: '#64748B' }}>Đang tải danh sách đã chặn...</Text>
            </div>
          ) : blockedUsers.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: 80,
                gap: 16,
              }}
            >
              <div style={{ fontSize: 56, lineHeight: 1 }}>🛡️</div>
              <Title level={4} style={{ color: '#64748B', margin: 0, fontWeight: 700 }}>
                Không có ai bị chặn
              </Title>
              <Text style={{ color: '#475569', textAlign: 'center', maxWidth: 320 }}>
                Bạn chưa chặn người dùng nào.
              </Text>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {blockedUsers.map((u) => (
                <BlockedUserCard
                  key={u.senderId}
                  user={u}
                  onUnblock={unblockUser}
                  unblockLoading={unblockLoading}
                />
              ))}

              {blockedHasMore && (
                <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                  <Button
                    onClick={blockedLoadMore}
                    loading={blockedLoading}
                    style={{
                      background: 'rgba(255,255,255,0.05)',
                      borderColor: 'rgba(255,255,255,0.1)',
                      color: '#94A3B8',
                      borderRadius: 8,
                    }}
                  >
                    Tải thêm
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
