import { useEffect, useRef, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  Layout, Typography, Input, Button, Avatar, Tooltip,
  Badge, Spin, Empty, Tag,
} from 'antd';
import {
  SendOutlined, TeamOutlined, WifiOutlined, DisconnectOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import { useWebSocket } from '../../hooks/useWebSocket';
import roomApi from '../../api/roomApi';
import messageApi from '../../api/messageApi';
import memberApi from '../../api/memberApi';
import PageLoading from '../../components/common/PageLoading';
import PageError from '../../components/common/PageError';
import dayjs from 'dayjs';

const { Sider, Content } = Layout;
const { Text, Title } = Typography;

function MessageBubble({ message, isOwn }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: isOwn ? 'row-reverse' : 'row',
        gap: 8,
        marginBottom: 12,
        alignItems: 'flex-end',
      }}
    >
      {!isOwn && (
        <Avatar size={32} style={{ backgroundColor: '#1677ff', flexShrink: 0 }}>
          {message.senderName?.[0]?.toUpperCase()}
        </Avatar>
      )}
      <div style={{ maxWidth: '65%' }}>
        {!isOwn && (
          <Text
            type="secondary"
            style={{ fontSize: 11, display: 'block', marginBottom: 2, paddingLeft: 2 }}
          >
            {message.senderName}
          </Text>
        )}
        <div
          style={{
            padding: '8px 14px',
            borderRadius: isOwn ? '16px 4px 16px 16px' : '4px 16px 16px 16px',
            backgroundColor: isOwn ? '#1677ff' : '#fff',
            color: isOwn ? '#fff' : '#1a1a1a',
            border: isOwn ? 'none' : '1px solid #f0f0f0',
            fontSize: 14,
            lineHeight: 1.5,
            wordBreak: 'break-word',
          }}
        >
          {message.content}
        </div>
        <Text
          type="secondary"
          style={{
            fontSize: 10,
            display: 'block',
            textAlign: isOwn ? 'right' : 'left',
            marginTop: 2,
            paddingLeft: 2,
            paddingRight: 2,
          }}
        >
          {dayjs(message.createdAt).format('HH:mm')}
        </Text>
      </div>
    </div>
  );
}

export default function ChatPage() {
  const { roomSlug } = useParams();
  const { currentUser } = useAuth();

  const [room, setRoom] = useState(null);
  const [messages, setMessages] = useState([]);
  const [members, setMembers] = useState([]);
  const [roomLoading, setRoomLoading] = useState(true);
  const [roomError, setRoomError] = useState(null);
  const [msgInput, setMsgInput] = useState('');
  const [sending, setSending] = useState(false);
  const [showMembers, setShowMembers] = useState(true);

  const messagesEndRef = useRef(null);
  const userScrolledUp = useRef(false);
  const containerRef = useRef(null);

  // Fetch room info
  useEffect(() => {
    async function fetchRoom() {
      setRoomLoading(true);
      setRoomError(null);
      try {
        const res = await roomApi.getRoomBySlug(roomSlug);
        setRoom(res.data);

        // Fetch messages and members in parallel
        const [msgRes, memberRes] = await Promise.allSettled([
          messageApi.getMessages(res.data.id, { size: 50 }),
          memberApi.getRoomMembers(res.data.id),
        ]);

        if (msgRes.status === 'fulfilled') {
          const data = msgRes.value.data?.content || msgRes.value.data || [];
          setMessages(data);
        }
        if (memberRes.status === 'fulfilled') {
          setMembers(memberRes.value.data?.content || memberRes.value.data || []);
        }
      } catch (err) {
        setRoomError(err.response?.data?.message || 'Failed to load room.');
      } finally {
        setRoomLoading(false);
      }
    }
    fetchRoom();
  }, [roomSlug]);

  // Handle incoming WebSocket messages
  const handleIncomingMessage = useCallback((newMessage) => {
    setMessages((prev) => [...prev, newMessage]);
  }, []);

  const { connected, sendMessage } = useWebSocket(room?.id || null, handleIncomingMessage);

  // Auto-scroll only when user is at the bottom
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const isAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 80;
    if (!userScrolledUp.current || isAtBottom) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleScroll = () => {
    const container = containerRef.current;
    if (!container) return;
    const isAtBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 80;
    userScrolledUp.current = !isAtBottom;
  };

  const handleSend = async () => {
    const content = msgInput.trim();
    if (!content || !room) return;

    setMsgInput('');
    setSending(true);

    // Optimistic message
    const optimistic = {
      id: `temp-${Date.now()}`,
      roomId: room.id,
      senderId: currentUser?.id,
      senderName: currentUser?.displayName || currentUser?.username,
      content,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimistic]);

    try {
      sendMessage({ content, senderId: currentUser?.id });
    } catch {
      // If WS fails, the optimistic message stays — REST fallback could be added here
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  if (roomLoading) return <PageLoading />;
  if (roomError) return <PageError message={roomError} />;

  return (
    <Layout
      style={{
        height: 'calc(100vh - 56px - 48px)',
        borderRadius: 8,
        overflow: 'hidden',
        border: '1px solid #f0f0f0',
        backgroundColor: '#fff',
      }}
    >
      {/* Left: Room Info Bar */}
      <div
        style={{
          width: 240,
          borderRight: '1px solid #f0f0f0',
          padding: '16px 0',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
        }}
      >
        <div style={{ padding: '0 16px 16px', borderBottom: '1px solid #f0f0f0' }}>
          <Title level={5} style={{ margin: 0 }}>
            {room?.name}
          </Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            #{room?.slug}
          </Text>
          {room?.description && (
            <Text
              type="secondary"
              style={{ fontSize: 12, display: 'block', marginTop: 4 }}
            >
              {room.description}
            </Text>
          )}
        </div>
        <div style={{ padding: '12px 16px' }}>
          <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
            Members ({members.length})
          </Text>
          <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
            {members.slice(0, 15).map((m) => (
              <div key={m.id || m.userId} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size={24} style={{ backgroundColor: '#1677ff', fontSize: 11 }}>
                  {(m.displayName || m.username)?.[0]?.toUpperCase()}
                </Avatar>
                <Text style={{ fontSize: 13 }}>{m.displayName || m.username}</Text>
                {m.role === 'OWNER' && <Tag color="blue" style={{ fontSize: 10, padding: '0 4px', margin: 0 }}>Owner</Tag>}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Center: Chat */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Chat header */}
        <div
          style={{
            padding: '12px 20px',
            borderBottom: '1px solid #f0f0f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexShrink: 0,
          }}
        >
          <Text strong style={{ fontSize: 15 }}>
            # {room?.name}
          </Text>
          <Tooltip title={connected ? 'Connected' : 'Disconnected'}>
            <Tag
              color={connected ? 'success' : 'error'}
              icon={connected ? <WifiOutlined /> : <DisconnectOutlined />}
            >
              {connected ? 'Live' : 'Offline'}
            </Tag>
          </Tooltip>
        </div>

        {/* Messages area */}
        <div
          ref={containerRef}
          onScroll={handleScroll}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '20px',
          }}
        >
          {messages.length === 0 ? (
            <Empty
              description="No messages yet. Start the conversation!"
              style={{ marginTop: 60 }}
            />
          ) : (
            messages.map((msg) => (
              <MessageBubble
                key={msg.id}
                message={msg}
                isOwn={msg.senderId === currentUser?.id}
              />
            ))
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Composer */}
        <div
          style={{
            padding: '12px 16px',
            borderTop: '1px solid #f0f0f0',
            display: 'flex',
            gap: 8,
            alignItems: 'flex-end',
            flexShrink: 0,
          }}
        >
          <Input.TextArea
            value={msgInput}
            onChange={(e) => setMsgInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Write a message... (Enter to send)"
            autoSize={{ minRows: 1, maxRows: 4 }}
            style={{ flex: 1, resize: 'none', borderRadius: 8 }}
            disabled={sending}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            loading={sending}
            disabled={!msgInput.trim()}
            style={{ height: 38 }}
          >
            Send
          </Button>
        </div>
      </div>
    </Layout>
  );
}
