import { useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Layout, Input, Button, Avatar, Typography,
  Tag, Spin, Upload, Image, Tooltip, message as antMessage,
} from 'antd';
import {
  SendOutlined, WifiOutlined, DisconnectOutlined,
  PlusCircleOutlined, SearchOutlined, PictureOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import { useDirectChat } from '../../hooks/useDirectChat';
import conversationApi from '../../api/conversationApi';
import fileApi from '../../api/fileApi';
import ConversationList from './ConversationList';
import SearchUserPanel from './SearchUserPanel';
import dayjs from 'dayjs';

const { Sider } = Layout;
const { Text, Title } = Typography;

// ─── Message Bubble ───────────────────────────────────────────────────────────
function MessageBubble({ msg, isOwn }) {
  const isImage = msg.type === 'IMAGE';
  const imageUrls = msg.mediaUrls?.length
    ? msg.mediaUrls
    : msg.mediaUrl
    ? msg.mediaUrl.split(',').filter(Boolean)
    : [];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: isOwn ? 'row-reverse' : 'row',
        gap: 10,
        marginBottom: 16,
        alignItems: 'flex-end',
      }}
    >
      {/* Avatar người gửi (chỉ hiện khi không phải tin của mình) */}
      {!isOwn && (
        <Tooltip title={msg.sender?.name}>
          <Avatar
            size={32}
            src={msg.sender?.avatar}
            style={{ background: 'linear-gradient(135deg, #6366F1, #00F2FE)', flexShrink: 0, fontWeight: 700 }}
          >
            {!msg.sender?.avatar && msg.sender?.name?.[0]?.toUpperCase()}
          </Avatar>
        </Tooltip>
      )}

      <div style={{ maxWidth: '68%' }}>
        {!isOwn && (
          <Text style={{ fontSize: 11, color: '#64748B', display: 'block', marginBottom: 4, paddingLeft: 4 }}>
            {msg.sender?.name}
          </Text>
        )}

        {/* Image grid */}
        {isImage && imageUrls.length > 0 && (
          <Image.PreviewGroup>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: imageUrls.length === 1 ? '1fr' : 'repeat(2, 1fr)',
                gap: 4,
                marginBottom: msg.content ? 6 : 0,
              }}
            >
              {imageUrls.map((url, i) => (
                <Image
                  key={i}
                  src={url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'}/v1/files/messages/${url}`}
                  alt={`image-${i}`}
                  style={{ borderRadius: 10, maxWidth: 220, objectFit: 'cover', cursor: 'pointer' }}
                  preview={{ mask: <PictureOutlined /> }}
                />
              ))}
            </div>
          </Image.PreviewGroup>
        )}

        {/* Text content */}
        {msg.content && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: isOwn ? '18px 4px 18px 18px' : '4px 18px 18px 18px',
              background: isOwn
                ? 'linear-gradient(135deg, #6366F1, #4F46E5)'
                : 'rgba(255,255,255,0.06)',
              color: '#F1F5F9',
              fontSize: 14,
              lineHeight: 1.6,
              wordBreak: 'break-word',
              backdropFilter: 'blur(8px)',
              border: isOwn ? 'none' : '1px solid rgba(255,255,255,0.07)',
            }}
          >
            {msg.content}
          </div>
        )}

        <Text style={{ fontSize: 10, color: '#475569', display: 'block', marginTop: 4, textAlign: isOwn ? 'right' : 'left', paddingLeft: 4 }}>
          {dayjs(msg.createdAt).format('HH:mm')}
        </Text>
      </div>
    </div>
  );
}

// ─── DirectMessagePage ────────────────────────────────────────────────────────
export default function DirectMessagePage() {
  const { conversationId: paramConvId } = useParams(); // 'new' hoặc số
  const location = useLocation();
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  // State conversation danh sách
  const [conversations, setConversations] = useState([]);
  const [convLoading, setConvLoading] = useState(true);

  // State conversation đang chọn
  const [selectedConv, setSelectedConv] = useState(null);     // ConversationResponseDTO
  const [messages, setMessages] = useState([]);
  const [msgLoading, setMsgLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);

  // Compose
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [uploading, setUploading] = useState(false);

  // Search panel
  const [searchOpen, setSearchOpen] = useState(false);

  const messagesEndRef = useRef(null);
  const containerRef = useRef(null);

  // Người kia (cho trường hợp "new" conversation chưa tạo)
  const targetUser = location.state?.targetUser ?? null;
  const otherUser = selectedConv?.otherUser ?? targetUser ?? null;

  // ── WebSocket Hook ──────────────────────────────────────────────────────
  const handleIncomingMessage = useCallback((newMsg) => {
    setMessages((prev) => {
      // 1. Tránh duplicate nếu messageId thực tế đã tồn tại
      if (prev.some((m) => m.messageId === newMsg.messageId)) return prev;

      // 2. Nếu là tin nhắn do chính mình gửi, tìm và thay thế optimistic message (temp-*)
      const isOwn = String(newMsg.sender?.userId) === String(currentUser?.id);
      if (isOwn) {
        const tempIndex = prev.findIndex(
          (m) =>
            typeof m.messageId === 'string' &&
            m.messageId.startsWith('temp-') &&
            m.type === newMsg.type &&
            (newMsg.type === 'IMAGE' || m.content === newMsg.content)
        );
        if (tempIndex !== -1) {
          const updated = [...prev];
          updated[tempIndex] = newMsg;
          return updated;
        }
      }

      // 3. Tin nhắn từ người khác (hoặc không tìm thấy temp tương ứng) -> thêm vào cuối
      return [...prev, newMsg];
    });

    // Cập nhật preview tin nhắn cuối trong danh sách hội thoại
    setConversations((prevList) =>
      prevList.map((c) => {
        if (c.conversationId === newMsg.conversationId) {
          return {
            ...c,
            lastMessage: {
              messageId: newMsg.messageId,
              type: newMsg.type,
              content: newMsg.content,
              mediaUrl: newMsg.mediaUrl,
              senderId: newMsg.sender?.userId,
              createdAt: newMsg.createdAt,
            },
          };
        }
        return c;
      })
    );
  }, [currentUser?.id]);

  const activeConvId = selectedConv?.conversationId ?? null;
  const { connected, sendDM } = useDirectChat(activeConvId, handleIncomingMessage);

  // ── Load danh sách conversations ───────────────────────────────────────
  useEffect(() => {
    async function loadConversations() {
      setConvLoading(true);
      try {
        const res = await conversationApi.getAll();
        const data = res.data?.data ?? res.data ?? [];
        setConversations(data);

        // Nếu URL là /direct/:id (số), tự động chọn conversation đó
        if (paramConvId && paramConvId !== 'new') {
          const found = data.find((c) => String(c.conversationId) === String(paramConvId));
          if (found) handleSelectConversation(found);
        }
      } catch {
        antMessage.error('Không thể tải danh sách hội thoại.');
      } finally {
        setConvLoading(false);
      }
    }
    loadConversations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Chọn conversation ──────────────────────────────────────────────────
  const handleSelectConversation = useCallback(async (conv) => {
    setSelectedConv(conv);
    setMessages([]);
    setPage(0);
    setHasMore(true);
    navigate(`/direct/${conv.conversationId}`, { replace: true, state: location.state });
    await loadMessages(conv.conversationId, 0);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  // ── Load lịch sử tin nhắn ─────────────────────────────────────────────
  async function loadMessages(convId, pageNum) {
    setMsgLoading(true);
    try {
      const res = await conversationApi.getMessages(convId, pageNum, 30);
      const paged = res.data?.data ?? res.data;
      const items = paged?.content ?? paged ?? [];
      if (pageNum === 0) {
        setMessages(items);
      } else {
        setMessages((prev) => [...items, ...prev]);
      }
      setHasMore(!(paged?.last ?? items.length < 30));
    } catch {
      antMessage.error('Không thể tải tin nhắn.');
    } finally {
      setMsgLoading(false);
    }
  }

  // ── Auto-scroll bottom ─────────────────────────────────────────────────
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // ── Load more (scroll up) ──────────────────────────────────────────────
  const handleScroll = async () => {
    const el = containerRef.current;
    if (!el || !hasMore || msgLoading || !selectedConv) return;
    if (el.scrollTop < 80) {
      const nextPage = page + 1;
      setPage(nextPage);
      await loadMessages(selectedConv.conversationId, nextPage);
    }
  };

  // ── Gửi tin nhắn text ─────────────────────────────────────────────────
  const handleSend = async () => {
    const content = text.trim();
    if (!content) return;
    setSending(true);
    setText('');

    const payload = {
      conversationId: selectedConv?.conversationId ?? null,
      targetUserId: !selectedConv ? targetUser?.id : null,
      type: 'TEXT',
      content,
    };

    // Optimistic message
    const optimistic = {
      messageId: `temp-${Date.now()}`,
      conversationId: selectedConv?.conversationId,
      sender: { userId: currentUser?.id, name: currentUser?.name || currentUser?.email },
      type: 'TEXT',
      content,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimistic]);

    try {
      sendDM(payload);
      // Nếu đây là conversation mới, cần reload danh sách để lấy conversationId
      if (!selectedConv) {
        const res = await conversationApi.getAll();
        const data = res.data?.data ?? res.data ?? [];
        setConversations(data);
        const newConv = data.find((c) => c.otherUser?.userId === targetUser?.id);
        if (newConv) {
          setSelectedConv(newConv);
          navigate(`/direct/${newConv.conversationId}`, { replace: true });
        }
      }
    } catch {
      antMessage.error('Không gửi được tin nhắn.');
    } finally {
      setSending(false);
    }
  };

  // ── Upload ảnh ────────────────────────────────────────────────────────
  const handleUpload = async ({ file }) => {
    if (!otherUser && !selectedConv) {
      antMessage.warning('Vui lòng chọn người nhận trước.');
      return;
    }
    setUploading(true);
    try {
      const uploadRes = await fileApi.upload(file, 'messages');
      const url = uploadRes.data?.data?.url ?? uploadRes.data?.url;

      const payload = {
        conversationId: selectedConv?.conversationId ?? null,
        targetUserId: !selectedConv ? targetUser?.id : null,
        type: 'IMAGE',
        content: '',
        mediaUrls: [url],
        mediaUrl: url,
      };
      sendDM(payload);

      // Optimistic image message
      const optimistic = {
        messageId: `temp-img-${Date.now()}`,
        conversationId: selectedConv?.conversationId,
        sender: { userId: currentUser?.id, name: currentUser?.name || currentUser?.email },
        type: 'IMAGE',
        mediaUrls: [url],
        mediaUrl: url,
        createdAt: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, optimistic]);
    } catch {
      antMessage.error('Upload ảnh thất bại.');
    } finally {
      setUploading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────
  return (
    <div
      id="direct-message-page"
      style={{
        display: 'flex',
        height: 'calc(100vh - 60px)',
        overflow: 'hidden',
        background: '#080B11',
      }}
    >
      {/* ── Left Sidebar: Conversation List ── */}
      <div
        style={{
          width: 280,
          flexShrink: 0,
          display: 'flex',
          flexDirection: 'column',
          borderRight: '1px solid rgba(255,255,255,0.05)',
          background: '#0C101A',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '16px 16px 12px',
            borderBottom: '1px solid rgba(255,255,255,0.05)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Title level={5} style={{ color: '#F1F5F9', margin: 0, fontWeight: 700 }}>
            💬 Tin nhắn
          </Title>
          <Tooltip title="Tìm kiếm và nhắn tin người mới">
            <Button
              id="btn-open-search"
              type="text"
              shape="circle"
              icon={<SearchOutlined style={{ color: '#6366F1', fontSize: 18 }} />}
              onClick={() => setSearchOpen(true)}
              style={{
                background: 'rgba(99,102,241,0.12)',
                border: '1px solid rgba(99,102,241,0.25)',
              }}
            />
          </Tooltip>
        </div>

        {/* Conversation list */}
        <div style={{ flex: 1, overflowY: 'auto', paddingTop: 8 }}>
          <ConversationList
            conversations={conversations}
            selectedId={selectedConv?.conversationId}
            onSelect={handleSelectConversation}
            loading={convLoading}
            currentUserId={currentUser?.id}
          />
        </div>
      </div>

      {/* ── Main Chat Area ── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {!selectedConv && !targetUser ? (
          /* Empty state */
          <div
            style={{
              flex: 1, display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center', gap: 16,
            }}
          >
            <div style={{ fontSize: 56 }}>💬</div>
            <Title level={4} style={{ color: '#64748B', margin: 0 }}>
              Chọn một hội thoại để bắt đầu
            </Title>
            <Text style={{ color: '#475569' }}>
              Hoặc nhấn{' '}
              <Button
                id="btn-open-search-center"
                type="link"
                style={{ color: '#6366F1', padding: 0 }}
                icon={<SearchOutlined />}
                onClick={() => setSearchOpen(true)}
              >
                Tìm kiếm người dùng
              </Button>{' '}
              để nhắn tin mới.
            </Text>
          </div>
        ) : (
          <>
            {/* Chat header */}
            <div
              style={{
                padding: '14px 20px',
                borderBottom: '1px solid rgba(255,255,255,0.05)',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                background: '#0F1422',
                flexShrink: 0,
              }}
            >
              <Avatar
                size={38}
                src={otherUser?.avatar}
                style={{ background: 'linear-gradient(135deg, #6366F1, #00F2FE)', fontWeight: 700 }}
              >
                {!otherUser?.avatar && otherUser?.name?.[0]?.toUpperCase()}
              </Avatar>
              <div style={{ flex: 1 }}>
                <Text style={{ color: '#F1F5F9', fontWeight: 700, fontSize: 15, display: 'block' }}>
                  {otherUser?.name || otherUser?.username || 'Người dùng'}
                </Text>
                <Text style={{ color: '#64748B', fontSize: 12 }}>
                  @{otherUser?.username || '—'}
                </Text>
              </div>
              {/* WebSocket status */}
              <Tag
                color={connected ? 'success' : 'default'}
                icon={connected ? <WifiOutlined /> : <DisconnectOutlined />}
                style={{ borderRadius: 20, fontSize: 11, padding: '0 8px' }}
              >
                {connected ? 'Live' : 'Offline'}
              </Tag>
            </div>

            {/* Messages area */}
            <div
              ref={containerRef}
              onScroll={handleScroll}
              style={{
                flex: 1,
                overflowY: 'auto',
                padding: '20px 24px',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              {msgLoading && (
                <div style={{ textAlign: 'center', marginBottom: 12 }}>
                  <Spin size="small" />
                </div>
              )}
              {messages.map((msg, i) => (
                <MessageBubble
                  key={msg.messageId ?? i}
                  msg={msg}
                  isOwn={
                    msg.sender?.userId === currentUser?.id ||
                    msg.senderId === currentUser?.id
                  }
                />
              ))}
              <div ref={messagesEndRef} />
            </div>

            {/* Composer */}
            <div
              style={{
                padding: '12px 16px',
                borderTop: '1px solid rgba(255,255,255,0.05)',
                background: '#0F1422',
                display: 'flex',
                alignItems: 'flex-end',
                gap: 8,
                flexShrink: 0,
              }}
            >
              {/* Upload ảnh */}
              <Upload
                id="upload-image-btn"
                accept="image/*"
                showUploadList={false}
                customRequest={handleUpload}
                disabled={uploading}
              >
                <Tooltip title="Gửi ảnh">
                  <Button
                    type="text"
                    shape="circle"
                    icon={
                      uploading
                        ? <Spin size="small" />
                        : <PictureOutlined style={{ fontSize: 18, color: '#6366F1' }} />
                    }
                    style={{
                      background: 'rgba(99,102,241,0.10)',
                      border: '1px solid rgba(99,102,241,0.2)',
                      width: 40, height: 40,
                    }}
                  />
                </Tooltip>
              </Upload>

              {/* Text input */}
              <Input.TextArea
                id="dm-text-input"
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Nhắn tin... (Enter để gửi)"
                autoSize={{ minRows: 1, maxRows: 5 }}
                style={{
                  flex: 1,
                  resize: 'none',
                  background: '#151D2E',
                  borderColor: 'rgba(255,255,255,0.08)',
                  borderRadius: 12,
                  color: '#F1F5F9',
                  fontSize: 14,
                }}
                disabled={sending}
              />

              {/* Gửi */}
              <Button
                id="btn-send-dm"
                type="primary"
                shape="circle"
                icon={<SendOutlined />}
                onClick={handleSend}
                loading={sending}
                disabled={!text.trim() && !uploading}
                size="large"
                style={{
                  width: 44, height: 44,
                  background: text.trim()
                    ? 'linear-gradient(135deg, #6366F1, #4F46E5)'
                    : 'rgba(99,102,241,0.2)',
                  border: 'none',
                  boxShadow: text.trim() ? '0 0 16px rgba(99,102,241,0.5)' : 'none',
                  transition: 'all 0.2s ease',
                }}
              />
            </div>
          </>
        )}
      </div>

      {/* Search User Panel */}
      <SearchUserPanel open={searchOpen} onClose={() => setSearchOpen(false)} />
    </div>
  );
}
