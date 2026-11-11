import { Avatar, Badge, Typography, Spin, Empty } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/vi';

dayjs.extend(relativeTime);
dayjs.locale('vi');

const { Text } = Typography;

/**
 * Danh sách các hội thoại 1-1 bên trái trang DirectMessage.
 *
 * @param {{
 *   conversations: ConversationResponseDTO[],
 *   selectedId: number|null,
 *   onSelect: function,
 *   loading: boolean,
 *   currentUserId: number
 * }} props
 */
export default function ConversationList({
  conversations,
  selectedId,
  onSelect,
  loading,
  currentUserId,
}) {
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 40 }}>
        <Spin />
      </div>
    );
  }

  if (conversations.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={
          <Text style={{ color: '#64748B', fontSize: 13 }}>
            Chưa có hội thoại nào.<br />Tìm kiếm và nhắn tin với ai đó!
          </Text>
        }
        style={{ marginTop: 40 }}
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, padding: '4px 8px' }}>
      {conversations.map((conv) => {
        const isSelected = conv.conversationId === selectedId;
        const other = conv.otherUser;
        const lastMsg = conv.lastMessage;
        const isOwnLast = lastMsg?.senderId === currentUserId;

        // Preview text cho lastMessage
        let previewText = 'Bắt đầu cuộc trò chuyện...';
        if (lastMsg) {
          if (lastMsg.type === 'IMAGE') previewText = `${isOwnLast ? 'Bạn' : other?.name || 'Họ'}: 🖼 Đã gửi ảnh`;
          else if (lastMsg.content) previewText = `${isOwnLast ? 'Bạn' : (other?.name?.split(' ').pop() || 'Họ')}: ${lastMsg.content}`;
        }

        return (
          <button
            key={conv.conversationId}
            id={`conv-item-${conv.conversationId}`}
            onClick={() => onSelect(conv)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '10px 12px',
              borderRadius: 10,
              cursor: 'pointer',
              background: isSelected
                ? 'rgba(99,102,241,0.2)'
                : 'transparent',
              border: isSelected
                ? '1px solid rgba(99,102,241,0.4)'
                : '1px solid transparent',
              transition: 'all 0.2s ease',
              width: '100%',
              textAlign: 'left',
            }}
            onMouseEnter={(e) => {
              if (!isSelected) e.currentTarget.style.background = 'rgba(255,255,255,0.04)';
            }}
            onMouseLeave={(e) => {
              if (!isSelected) e.currentTarget.style.background = 'transparent';
            }}
          >
            {/* Avatar người kia */}
            <div style={{ position: 'relative', flexShrink: 0 }}>
              <Avatar
                size={42}
                src={other?.avatar}
                style={{
                  background: 'linear-gradient(135deg, #6366F1, #00F2FE)',
                  fontWeight: 700,
                  fontSize: 16,
                }}
              >
                {!other?.avatar && (other?.name || other?.username)?.[0]?.toUpperCase() || <MessageOutlined />}
              </Avatar>
              {/* Online indicator (visual only) */}
              <span style={{
                position: 'absolute', bottom: 1, right: 1,
                width: 10, height: 10,
                borderRadius: '50%',
                background: '#10B981',
                border: '2px solid #0C101A',
              }} />
            </div>

            {/* Tên + preview tin nhắn */}
            <div style={{ flex: 1, overflow: 'hidden' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Text
                  style={{
                    color: '#F1F5F9',
                    fontWeight: isSelected ? 700 : 600,
                    fontSize: 14,
                    lineHeight: 1.3,
                  }}
                  ellipsis
                >
                  {other?.name || other?.username || 'Unknown'}
                </Text>
                {lastMsg?.createdAt && (
                  <Text style={{ color: '#475569', fontSize: 11, flexShrink: 0, marginLeft: 8 }}>
                    {dayjs(lastMsg.createdAt).fromNow(true)}
                  </Text>
                )}
              </div>
              <Text
                style={{
                  color: '#64748B',
                  fontSize: 12,
                  display: 'block',
                  marginTop: 2,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  maxWidth: 150,
                }}
              >
                {previewText}
              </Text>
            </div>
          </button>
        );
      })}
    </div>
  );
}
