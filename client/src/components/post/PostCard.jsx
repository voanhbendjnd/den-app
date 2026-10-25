import React, { useState } from 'react';
import { Avatar, Typography, Tag, Button, Tooltip, Image, Dropdown, Modal } from 'antd';
import {
  HeartOutlined,
  HeartFilled,
  CommentOutlined,
  ShareAltOutlined,
  GlobalOutlined,
  TeamOutlined,
  LockOutlined,
  UserOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleFilled,
} from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import EditPostModal from './EditPostModal';

const { Text, Paragraph } = Typography;

function formatRelativeTime(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now - date;
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHours = Math.floor(diffMin / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffSec < 60) return 'Vừa xong';
  if (diffMin < 60) return `${diffMin} phút trước`;
  if (diffHours < 24) return `${diffHours} giờ trước`;
  if (diffDays < 7) return `${diffDays} ngày trước`;

  return date.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

function renderPrivacyTag(privacy) {
  switch (privacy?.toUpperCase()) {
    case 'FRIENDS_ONLY':
      return (
        <Tag
          icon={<TeamOutlined />}
          style={{
            background: 'rgba(99, 102, 241, 0.15)',
            color: '#818CF8',
            borderColor: 'rgba(99, 102, 241, 0.3)',
            borderRadius: 6,
            fontSize: 11,
            margin: 0,
          }}
        >
          Bạn bè
        </Tag>
      );
    case 'PRIVATE':
      return (
        <Tag
          icon={<LockOutlined />}
          style={{
            background: 'rgba(239, 68, 68, 0.15)',
            color: '#F87171',
            borderColor: 'rgba(239, 68, 68, 0.3)',
            borderRadius: 6,
            fontSize: 11,
            margin: 0,
          }}
        >
          Chỉ mình tôi
        </Tag>
      );
    case 'PUBLIC':
    default:
      return (
        <Tag
          icon={<GlobalOutlined />}
          style={{
            background: 'rgba(0, 242, 254, 0.12)',
            color: '#00F2FE',
            borderColor: 'rgba(0, 242, 254, 0.3)',
            borderRadius: 6,
            fontSize: 11,
            margin: 0,
          }}
        >
          Công khai
        </Tag>
      );
  }
}

export default function PostCard({ post, onLike, onEdit, onDelete }) {
  const { currentUser } = useAuth();
  const [isBouncing, setIsBouncing] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);

  if (!post) return null;

  const {
    id,
    userId,
    authorName,
    authorUsername,
    authorAvatar,
    content,
    privacy,
    likeCounts = 0,
    mediaUrl,
    createdDate,
    isLiked = false,
  } = post;

  const isAuthor = Boolean(currentUser && (currentUser.id === userId));
  const displayName = authorName || authorUsername || 'Người dùng';
  const relativeTime = formatRelativeTime(createdDate);

  const handleLikeClick = () => {
    setIsBouncing(true);
    setTimeout(() => setIsBouncing(false), 300);
    onLike?.(id);
  };

  const handleDeleteClick = () => {
    Modal.confirm({
      title: 'Xóa bài viết?',
      icon: <ExclamationCircleFilled style={{ color: '#EF4444' }} />,
      content: 'Bạn có chắc chắn muốn xóa bài viết này không? Bài viết sau khi xóa sẽ không thể khôi phục.',
      okText: 'Xóa bài viết',
      okType: 'danger',
      cancelText: 'Hủy',
      centered: true,
      onOk: async () => {
        await onDelete?.(id);
      },
    });
  };

  const menuItems = [
    {
      key: 'edit',
      label: 'Chỉnh sửa bài viết',
      icon: <EditOutlined style={{ color: '#818CF8' }} />,
      onClick: () => setEditModalOpen(true),
    },
    {
      type: 'divider',
    },
    {
      key: 'delete',
      label: <span style={{ color: '#EF4444' }}>Xóa bài viết</span>,
      icon: <DeleteOutlined style={{ color: '#EF4444' }} />,
      onClick: handleDeleteClick,
    },
  ];

  return (
    <div
      className="glass-card"
      style={{
        background: 'linear-gradient(145deg, rgba(22, 30, 46, 0.85) 0%, rgba(15, 23, 42, 0.95) 100%)',
        border: '1px solid rgba(99, 102, 241, 0.2)',
        borderRadius: 18,
        padding: '20px 22px',
        marginBottom: 20,
        boxShadow: '0 8px 24px rgba(0, 0, 0, 0.3)',
        transition: 'transform 0.2s ease, box-shadow 0.2s ease',
      }}
    >
      {/* Header: Author info & metadata */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 14,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Avatar
            size={46}
            src={authorAvatar}
            icon={!authorAvatar && <UserOutlined />}
            style={{
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              fontWeight: 700,
              flexShrink: 0,
              boxShadow: '0 2px 8px rgba(99, 102, 241, 0.4)',
            }}
          >
            {displayName.charAt(0).toUpperCase()}
          </Avatar>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <Text
                style={{
                  color: '#F1F5F9',
                  fontWeight: 700,
                  fontSize: 15,
                  letterSpacing: '-0.2px',
                }}
              >
                {displayName}
              </Text>
              {authorUsername && (
                <Text style={{ color: '#64748B', fontSize: 13 }}>
                  @{authorUsername}
                </Text>
              )}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 }}>
              <Text style={{ color: '#94A3B8', fontSize: 12 }}>
                {relativeTime}
              </Text>
              <span style={{ color: '#475569', fontSize: 10 }}>•</span>
              {renderPrivacyTag(privacy)}
            </div>
          </div>
        </div>

        {/* Author Action Menu: Edit & Delete */}
        {isAuthor && (
          <Dropdown menu={{ items: menuItems }} placement="bottomRight" trigger={['click']}>
            <Button
              type="text"
              icon={<MoreOutlined style={{ color: '#94A3B8', fontSize: 18 }} />}
              style={{
                borderRadius: 8,
                padding: '4px 8px',
                color: '#94A3B8',
              }}
            />
          </Dropdown>
        )}
      </div>

      {/* Body: Post Content */}
      {content && (
        <Paragraph
          style={{
            color: '#E2E8F0',
            fontSize: 14.5,
            lineHeight: 1.65,
            marginBottom: mediaUrl ? 14 : 16,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {content}
        </Paragraph>
      )}

      {/* Body: Attached Media Image */}
      {mediaUrl && (
        <div
          style={{
            marginBottom: 16,
            borderRadius: 14,
            overflow: 'hidden',
            background: 'rgba(0, 0, 0, 0.3)',
            border: '1px solid rgba(255, 255, 255, 0.08)',
          }}
        >
          <Image
            src={mediaUrl}
            alt="Post media"
            style={{
              width: '100%',
              maxHeight: 450,
              objectFit: 'cover',
              borderRadius: 14,
            }}
            fallback="https://placehold.co/600x400/1e293b/94a3b8?text=Image+Unavailable"
          />
        </div>
      )}

      {/* Footer: Action bar (Like, Comment, Share) */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingTop: 12,
          borderTop: '1px solid rgba(255, 255, 255, 0.07)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          {/* Like button */}
          <Button
            type="text"
            icon={
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  transform: isBouncing ? 'scale(1.35)' : 'scale(1)',
                  transition: 'transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275)',
                }}
              >
                {isLiked ? (
                  <HeartFilled
                    style={{
                      color: '#EF4444',
                      fontSize: 17,
                      filter: 'drop-shadow(0 0 6px rgba(239, 68, 68, 0.6))',
                    }}
                  />
                ) : (
                  <HeartOutlined style={{ color: '#94A3B8', fontSize: 17 }} />
                )}
              </span>
            }
            onClick={handleLikeClick}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              color: isLiked ? '#F87171' : '#94A3B8',
              fontWeight: 600,
              fontSize: 13,
              padding: '4px 12px',
              borderRadius: 8,
              background: isLiked ? 'rgba(239, 68, 68, 0.15)' : 'rgba(255, 255, 255, 0.03)',
              border: isLiked ? '1px solid rgba(239, 68, 68, 0.3)' : '1px solid rgba(255, 255, 255, 0.06)',
              transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
            }}
          >
            <span>{likeCounts} Thích</span>
          </Button>

          {/* Comment button */}
          <Tooltip title="Bình luận (sắp ra mắt)">
            <Button
              type="text"
              icon={<CommentOutlined style={{ color: '#94A3B8', fontSize: 17 }} />}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                color: '#94A3B8',
                fontWeight: 600,
                fontSize: 13,
                padding: '4px 10px',
                borderRadius: 8,
              }}
            >
              <span>Bình luận</span>
            </Button>
          </Tooltip>
        </div>

        {/* Share button */}
        <Tooltip title="Chia sẻ">
          <Button
            type="text"
            icon={<ShareAltOutlined style={{ color: '#94A3B8', fontSize: 16 }} />}
            style={{
              borderRadius: 8,
              color: '#94A3B8',
            }}
          />
        </Tooltip>
      </div>

      {/* Edit Post Modal */}
      {isAuthor && (
        <EditPostModal
          open={editModalOpen}
          post={post}
          onCancel={() => setEditModalOpen(false)}
          onSubmit={onEdit}
        />
      )}
    </div>
  );
}

