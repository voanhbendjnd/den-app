import { useState, useCallback, useRef } from 'react';
import {
  Drawer, Input, List, Avatar, Button, Tag, Typography,
  Space, Spin, Empty, message as antMessage,
} from 'antd';
import { SearchOutlined, UserAddOutlined, MessageOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import friendApi from '../../api/friendApi';
import conversationApi from '../../api/conversationApi';

const { Text } = Typography;

/**
 * Panel tìm kiếm người dùng và thực hiện:
 * - Gửi lời mời kết bạn
 * - Bắt đầu nhắn tin (tạo/mở conversation 1-1)
 *
 * @param {{ open: boolean, onClose: function }} props
 */
export default function SearchUserPanel({ open, onClose }) {
  const { currentUser } = useAuth();
  const navigate = useNavigate();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [friendLoading, setFriendLoading] = useState({});   // { [userId]: boolean }
  const [chatLoading, setChatLoading] = useState({});        // { [userId]: boolean }
  const [friendSent, setFriendSent] = useState({});          // { [userId]: boolean }

  const debounceTimer = useRef(null);

  const doSearch = useCallback(async (q) => {
    if (!q || q.trim().length < 2) {
      setResults([]);
      return;
    }
    setSearching(true);
    try {
      const res = await friendApi.searchUsers(q.trim());
      const data = res.data?.data ?? res.data ?? [];
      setResults(data.filter((u) => u.id !== currentUser?.id));
    } catch {
      antMessage.error('Không tìm được người dùng, thử lại sau.');
    } finally {
      setSearching(false);
    }
  }, [currentUser?.id]);

  const handleSearch = (e) => {
    const val = e.target.value;
    setQuery(val);
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    debounceTimer.current = setTimeout(() => doSearch(val), 400);
  };

  const handleSendFriendRequest = async (user) => {
    setFriendLoading((prev) => ({ ...prev, [user.id]: true }));
    try {
      await friendApi.sendFriendRequest(user.id);
      setFriendSent((prev) => ({ ...prev, [user.id]: true }));
      antMessage.success(`Đã gửi lời mời kết bạn tới ${user.name || user.username}!`);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể gửi lời mời kết bạn.';
      antMessage.error(msg);
    } finally {
      setFriendLoading((prev) => ({ ...prev, [user.id]: false }));
    }
  };

  const handleStartChat = async (user) => {
    setChatLoading((prev) => ({ ...prev, [user.id]: true }));
    try {
      // Lấy danh sách conversations để tìm conversation sẵn có với user này
      const convRes = await conversationApi.getAll();
      const conversations = convRes.data?.data ?? convRes.data ?? [];
      const existing = conversations.find(
        (c) => c.otherUser?.userId === user.id
      );

      onClose();
      if (existing) {
        // Mở conversation đã có
        navigate(`/direct/${existing.conversationId}`, {
          state: { otherUser: existing.otherUser },
        });
      } else {
        // Chuyển đến trang DM với targetUserId để tạo conversation khi gửi tin đầu
        navigate(`/direct/new`, {
          state: { targetUser: user },
        });
      }
    } catch {
      antMessage.error('Không thể mở hội thoại.');
    } finally {
      setChatLoading((prev) => ({ ...prev, [user.id]: false }));
    }
  };

  const handleClose = () => {
    setQuery('');
    setResults([]);
    setFriendSent({});
    onClose();
  };

  return (
    <Drawer
      title={
        <Space>
          <SearchOutlined style={{ color: '#6366F1' }} />
          <span style={{ fontWeight: 700, color: '#F1F5F9' }}>Tìm kiếm người dùng</span>
        </Space>
      }
      open={open}
      onClose={handleClose}
      width={400}
      placement="right"
      styles={{
        header: { background: '#0F1422', borderBottom: '1px solid rgba(255,255,255,0.06)' },
        body: { background: '#0C101A', padding: 20 },
        mask: { backdropFilter: 'blur(4px)' },
      }}
    >
      {/* Search input */}
      <Input
        id="search-user-input"
        prefix={<SearchOutlined style={{ color: '#64748B' }} />}
        placeholder="Nhập username để tìm kiếm..."
        value={query}
        onChange={handleSearch}
        size="large"
        allowClear
        style={{
          background: '#151D2E',
          borderColor: 'rgba(255,255,255,0.1)',
          borderRadius: 10,
          marginBottom: 20,
          color: '#F1F5F9',
        }}
      />

      {/* Results */}
      {searching ? (
        <div style={{ textAlign: 'center', paddingTop: 40 }}>
          <Spin size="large" />
          <Text style={{ display: 'block', marginTop: 12, color: '#64748B' }}>
            Đang tìm kiếm...
          </Text>
        </div>
      ) : results.length === 0 && query.length >= 2 ? (
        <Empty
          description={<span style={{ color: '#64748B' }}>Không tìm thấy người dùng</span>}
          style={{ marginTop: 40 }}
        />
      ) : (
        <List
          dataSource={results}
          renderItem={(user) => (
            <List.Item
              key={user.id}
              style={{
                background: '#151D2E',
                borderRadius: 12,
                marginBottom: 10,
                padding: '12px 16px',
                border: '1px solid rgba(255,255,255,0.05)',
              }}
              actions={[
                <Space key="actions" direction="vertical" size={6} style={{ alignItems: 'stretch' }}>
                  {/* Nút Kết bạn */}
                  {friendSent[user.id] ? (
                    <Tag color="success" style={{ textAlign: 'center', borderRadius: 8 }}>
                      ✓ Đã gửi
                    </Tag>
                  ) : (
                    <Button
                      id={`btn-friend-${user.id}`}
                      size="small"
                      icon={<UserAddOutlined />}
                      loading={!!friendLoading[user.id]}
                      onClick={() => handleSendFriendRequest(user)}
                      style={{
                        background: 'rgba(99,102,241,0.15)',
                        borderColor: '#6366F1',
                        color: '#A5B4FC',
                        borderRadius: 8,
                        fontSize: 12,
                      }}
                    >
                      Kết bạn
                    </Button>
                  )}
                  {/* Nút Nhắn tin */}
                  <Button
                    id={`btn-chat-${user.id}`}
                    size="small"
                    type="primary"
                    icon={<MessageOutlined />}
                    loading={!!chatLoading[user.id]}
                    onClick={() => handleStartChat(user)}
                    style={{ borderRadius: 8, fontSize: 12 }}
                  >
                    Nhắn tin
                  </Button>
                </Space>,
              ]}
            >
              <List.Item.Meta
                avatar={
                  <Avatar
                    size={42}
                    src={user.avatar}
                    style={{
                      background: 'linear-gradient(135deg, #6366F1, #00F2FE)',
                      fontSize: 16,
                      fontWeight: 700,
                    }}
                  >
                    {!user.avatar && (user.name || user.username)?.[0]?.toUpperCase()}
                  </Avatar>
                }
                title={
                  <Text style={{ color: '#F1F5F9', fontWeight: 600 }}>
                    {user.name || user.username}
                  </Text>
                }
                description={
                  <Text style={{ color: '#64748B', fontSize: 12 }}>
                    @{user.username}
                  </Text>
                }
              />
            </List.Item>
          )}
        />
      )}
    </Drawer>
  );
}
