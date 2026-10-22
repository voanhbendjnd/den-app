import { Typography, Row, Col, Button, Avatar, Tag, Empty, Spin } from 'antd';
import {
  PlusOutlined,
  ThunderboltFilled,
  CompassOutlined,
  NumberOutlined,
} from '@ant-design/icons';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import FriendFeedSection from '../../components/post/FriendFeedSection';

const { Title, Text, Paragraph } = Typography;

export default function HomePage() {
  const { currentUser } = useAuth();
  const navigate = useNavigate();
  const {
    servers = [],
    serversLoading = false,
    serversError,
    refreshServers,
    openCreateModal,
    openJoinModal,
    openCreateChannelModal
  } = useOutletContext() || {};

  return (
    <div style={{ padding: '36px 40px', maxWidth: 1100, margin: '0 auto' }}>
      {/* =========================================================
          Hero Banner
         ========================================================= */}
      <div
        className="glass-panel"
        style={{
          borderRadius: 20,
          padding: '36px 36px',
          marginBottom: 32,
          position: 'relative',
          overflow: 'hidden',
          background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(0, 242, 254, 0.05) 100%), #111726',
          border: '1px solid rgba(99, 102, 241, 0.2)',
          boxShadow: '0 16px 36px rgba(0, 0, 0, 0.4)',
        }}
      >
        <div style={{ position: 'relative', zIndex: 1 }}>
          <Title level={2} style={{ margin: 0, color: '#F1F5F9', fontWeight: 800, fontSize: 28, letterSpacing: '-0.5px' }}>
            Xin chào, <span className="tech-text-gradient">{currentUser?.displayName || currentUser?.username || 'Bạn'}</span>! 👋
          </Title>

          <Paragraph style={{ color: '#94A3B8', fontSize: 15, marginTop: 8, marginBottom: 20, maxWidth: 600 }}>
            Chào mừng bạn đến với DenHub. Bắt đầu bằng cách tạo không gian nhóm của riêng bạn hoặc tham gia server của bạn bè.
          </Paragraph>

          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Button
              type="primary"
              size="large"
              icon={<PlusOutlined />}
              onClick={() => openCreateModal?.()}
              style={{
                height: 44,
                padding: '0 24px',
                fontWeight: 700,
                background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                border: 'none',
                boxShadow: '0 4px 16px rgba(0, 242, 254, 0.3)',
              }}
            >
              Tạo Không Gian (Server) Mới
            </Button>
            <Button
              size="large"
              icon={<CompassOutlined />}
              onClick={() => openJoinModal?.()}
              style={{
                height: 44,
                padding: '0 24px',
                fontWeight: 600,
                background: 'rgba(255, 255, 255, 0.06)',
                color: '#F1F5F9',
                borderColor: 'rgba(255, 255, 255, 0.15)',
              }}
            >
              Tham Gia Server (ID)
            </Button>
          </div>
        </div>
      </div>

      {/* =========================================================
          My Servers Section (Synchronized with Far-Left Dock)
         ========================================================= */}
      <div style={{ marginBottom: 36 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
          <div>
            <Title level={4} style={{ margin: 0, color: '#F1F5F9', fontWeight: 700 }}>
              Không Gian Của Bạn{!serversLoading && !serversError ? ` (${servers.length})` : ''}
            </Title>
            <Text style={{ color: '#64748B', fontSize: 13 }}>
              Danh sách các Server mà bạn đang quản lý hoặc tham gia
            </Text>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button
              type="link"
              icon={<CompassOutlined />}
              onClick={() => openJoinModal?.()}
              style={{ color: '#818CF8', fontWeight: 600 }}
            >
              Tham gia bằng ID
            </Button>
            <Button
              type="link"
              icon={<PlusOutlined />}
              onClick={() => openCreateModal?.()}
              style={{ color: '#00F2FE', fontWeight: 600 }}
            >
              Tạo thêm Server
            </Button>
          </div>
        </div>

        {serversLoading ? (
          <div role="status" aria-label="Đang tải danh sách Server" style={{ padding: '48px 24px', textAlign: 'center' }}>
            <Spin />
            <div style={{ marginTop: 12 }}>Đang tải danh sách Server...</div>
          </div>
        ) : serversError ? (
          <div role="alert" className="glass-panel" style={{ padding: '48px 24px', borderRadius: 16, textAlign: 'center' }}>
            <Title level={4} style={{ color: '#F1F5F9' }}>Chưa tải được danh sách Server</Title>
            <Paragraph style={{ color: '#94A3B8' }}>Vui lòng thử lại để xem các Server của bạn.</Paragraph>
            <Button onClick={() => refreshServers?.()}>Thử lại</Button>
          </div>
        ) : servers.length === 0 ? (
          <div
            className="glass-panel"
            style={{
              padding: '48px 24px',
              borderRadius: 16,
              textAlign: 'center',
            }}
          >
            <Empty
              image={<ThunderboltFilled style={{ fontSize: 48, color: '#6366F1' }} />}
              imageStyle={{ height: 48, marginBottom: 16 }}
              description={
                <div>
                  <Title level={4} style={{ color: '#F1F5F9', margin: '0 0 6px' }}>Bạn chưa có Server nào</Title>
                  <Text style={{ color: '#94A3B8' }}>
                    Tạo ngay một server đầu tiên của riêng bạn hoặc tham gia server có sẵn!
                  </Text>
                </div>
              }
            >
              <div style={{ display: 'flex', gap: 12, justifyContent: 'center', marginTop: 16, flexWrap: 'wrap' }}>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  style={{ fontWeight: 600, background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)', border: 'none' }}
                  onClick={() => openCreateModal?.()}
                >
                  Tạo Server Đầu Tiên
                </Button>
                <Button
                  icon={<CompassOutlined />}
                  style={{ fontWeight: 600, background: 'rgba(255, 255, 255, 0.06)', color: '#F1F5F9', borderColor: 'rgba(255, 255, 255, 0.15)' }}
                  onClick={() => openJoinModal?.()}
                >
                  Tham Gia Bằng ID
                </Button>
              </div>
            </Empty>
          </div>
        ) : (
          <Row gutter={[16, 16]}>
            {servers.map((server) => (
              <Col xs={24} sm={12} md={8} key={server.id}>
                <div
                  className="glass-card"
                  style={{
                    padding: 20,
                    borderRadius: 16,
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                    <Avatar
                      size={50}
                      src={server.iconUrl}
                      style={{
                        background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                        fontWeight: 800,
                        fontSize: 18,
                        flexShrink: 0,
                      }}
                    >
                      {server.name?.charAt(0).toUpperCase()}
                    </Avatar>
                    <div style={{ overflow: 'hidden' }}>
                      <Title level={5} style={{ margin: 0, color: '#F1F5F9', fontWeight: 700, whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                        {server.name}
                      </Title>
                      <Tag color="cyan" style={{ margin: '4px 0 0', fontSize: 11, borderRadius: 4 }}>
                        Hoạt động
                      </Tag>
                    </div>
                  </div>

                  <div style={{ marginTop: 18, paddingTop: 12, borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: '#64748B', fontSize: 12 }}>
                      Mã #{server.id}
                    </span>
                    <Button
                      type="link"
                      size="small"
                      icon={<NumberOutlined style={{ fontSize: 13 }} />}
                      onClick={(e) => {
                        e.stopPropagation();
                        openCreateChannelModal?.(server);
                      }}
                      style={{
                        color: '#00F2FE',
                        fontSize: 12,
                        fontWeight: 600,
                        padding: '0 4px',
                        height: 24,
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      + Kênh Mới
                    </Button>
                  </div>
                </div>
              </Col>
            ))}
          </Row>
        )}
      </div>

      {/* =========================================================
          Friends Feed Section (Bảng tin bạn bè)
         ========================================================= */}
      <div style={{ maxWidth: 760, margin: '40px auto 0' }}>
        <FriendFeedSection />
      </div>
    </div>
  );
}
