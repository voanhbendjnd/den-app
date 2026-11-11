import React from 'react';
import { Typography, Button, Spin, Empty, Tag, Tooltip } from 'antd';
import {
  ReloadOutlined,
  CompassFilled,
  MessageOutlined,
  DownOutlined,
  CheckCircleFilled,
  DisconnectOutlined,
} from '@ant-design/icons';
import { useFriendPosts } from '../../hooks/useFriendPosts';
import PostCard from './PostCard';
import CreatePostCard from './CreatePostCard';

const { Title, Text, Paragraph } = Typography;

export default function FriendFeedSection() {
  const {
    posts,
    loading,
    loadingMore,
    hasMore,
    error,
    wsConnected,
    refresh,
    loadMore,
    toggleLike,
    createPost,
    editPost,
    deletePost,
  } = useFriendPosts(10);


  return (
    <div style={{ marginTop: 12 }}>
      {/* Header of Feed Section */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20,
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 2px 10px rgba(99, 102, 241, 0.4)',
              }}
            >
              <CompassFilled style={{ color: '#fff', fontSize: 16 }} />
            </div>
            <Title
              level={4}
              style={{
                margin: 0,
                color: '#F1F5F9',
                fontWeight: 700,
                letterSpacing: '-0.3px',
              }}
            >
              Bảng Tin Bạn Bè
            </Title>

            {/* Realtime Live Status Tag */}
            <Tooltip title={wsConnected ? 'Đã kết nối thời gian thực (nhận bài mới tự động)' : 'Đang kết nối lại thời gian thực...'}>
              <Tag
                icon={wsConnected ? <CheckCircleFilled /> : <DisconnectOutlined />}
                color={wsConnected ? 'cyan' : 'default'}
                style={{
                  borderRadius: 12,
                  fontSize: 11,
                  padding: '1px 8px',
                  marginLeft: 4,
                  cursor: 'help',
                }}
              >
                {wsConnected ? 'Trực tiếp' : 'Ngoại tuyến'}
              </Tag>
            </Tooltip>
          </div>
          <Text style={{ color: '#64748B', fontSize: 13, marginTop: 4, display: 'inline-block' }}>
            Khoảnh khắc và chia sẻ mới nhất từ bạn bè của bạn
          </Text>
        </div>

        <Button
          type="text"
          icon={<ReloadOutlined spin={loading} />}
          onClick={refresh}
          style={{
            color: '#818CF8',
            fontWeight: 600,
            borderRadius: 8,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          Làm mới
        </Button>
      </div>

      {/* Create New Post Section */}
      <CreatePostCard onSubmit={createPost} />

      {/* Main Content Area */}
      {loading ? (
        <div
          role="status"
          aria-label="Đang tải bảng tin bài viết"
          style={{ padding: '60px 24px', textAlign: 'center' }}
        >
          <Spin size="large" />
          <div style={{ marginTop: 16, color: '#94A3B8', fontSize: 14 }}>
            Đang tải những bài viết mới nhất...
          </div>
        </div>
      ) : error ? (
        <div
          role="alert"
          className="glass-panel"
          style={{
            padding: '36px 24px',
            borderRadius: 16,
            textAlign: 'center',
            background: 'rgba(239, 68, 68, 0.05)',
            border: '1px solid rgba(239, 68, 68, 0.2)',
          }}
        >
          <Title level={5} style={{ color: '#F87171' }}>
            {error}
          </Title>
          <Button
            type="primary"
            onClick={refresh}
            style={{
              marginTop: 12,
              background: '#EF4444',
              borderColor: '#EF4444',
            }}
          >
            Thử lại
          </Button>
        </div>
      ) : posts.length === 0 ? (
        <div
          className="glass-panel"
          style={{
            padding: '50px 24px',
            borderRadius: 18,
            textAlign: 'center',
            background: 'rgba(17, 23, 38, 0.6)',
            border: '1px solid rgba(99, 102, 241, 0.15)',
          }}
        >
          <Empty
            image={<MessageOutlined style={{ fontSize: 44, color: '#6366F1' }} />}
            imageStyle={{ height: 44, marginBottom: 16 }}
            description={
              <div>
                <Title level={5} style={{ color: '#F1F5F9', margin: '0 0 6px' }}>
                  Chưa có bài viết nào trên bảng tin
                </Title>
                <Paragraph style={{ color: '#94A3B8', maxWidth: 450, margin: '0 auto' }}>
                  Hãy kết nối thêm bạn bè hoặc đợi bạn bè đăng tải những chia sẻ thú vị đầu tiên!
                </Paragraph>
              </div>
            }
          />
        </div>
      ) : (
        <div>
          {/* Post cards list */}
          {posts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              onLike={toggleLike}
              onEdit={editPost}
              onDelete={deletePost}
            />
          ))}

          {/* Load More Button */}
          {hasMore && (
            <div style={{ textAlign: 'center', marginTop: 24, marginBottom: 12 }}>
              <Button
                type="primary"
                size="large"
                icon={<DownOutlined />}
                loading={loadingMore}
                onClick={loadMore}
                style={{
                  background: 'rgba(255, 255, 255, 0.06)',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  color: '#F1F5F9',
                  fontWeight: 600,
                  borderRadius: 12,
                  padding: '0 28px',
                  height: 42,
                }}
              >
                {loadingMore ? 'Đang tải thêm...' : 'Xem thêm bài viết cũ hơn'}
              </Button>
            </div>
          )}

          {!hasMore && posts.length > 0 && (
            <div style={{ textAlign: 'center', padding: '16px 0', color: '#64748B', fontSize: 13 }}>
              Bạn đã xem hết tất cả bài viết của bạn bè! 🎉
            </div>
          )}
        </div>
      )}
    </div>
  );
}
