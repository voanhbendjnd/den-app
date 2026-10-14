import { useState, useRef, useEffect } from 'react';
import { Modal, Button, Typography, Avatar, Alert, message, Tag, Space, Tooltip } from 'antd';
import {
  UserAddOutlined,
  CopyOutlined,
  CheckOutlined,
  ReloadOutlined,
  CloseOutlined,
  ClockCircleOutlined,
  ShareAltOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import serverApi from '../../api/serverApi';
import { copyToClipboard, getInviteErrorMessage, formatExpiresAt } from '../../utils/serverInvite';

const { Title, Text, Paragraph } = Typography;

export default function ServerInviteModal({ open, onClose, server }) {
  const [loading, setLoading] = useState(false);
  const [invite, setInvite] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);
  const [copied, setCopied] = useState(false);
  const submittingRef = useRef(false);
  const activeServerIdRef = useRef(null);

  // Reset state when opening or when server changes
  useEffect(() => {
    if (open) {
      if (activeServerIdRef.current !== server?.id) {
        setInvite(null);
        setErrorMsg(null);
        setCopied(false);
        activeServerIdRef.current = server?.id;
      }
      submittingRef.current = false;
    }
  }, [open, server?.id]);

  const handleClose = () => {
    if (loading) return;
    setErrorMsg(null);
    setCopied(false);
    onClose();
  };

  const handleCreateInvite = async () => {
    if (submittingRef.current || loading) return;
    if (!server?.id) {
      setErrorMsg('Không tìm thấy thông tin Server hợp lệ.');
      return;
    }

    submittingRef.current = true;
    setLoading(true);
    setErrorMsg(null);
    setCopied(false);

    try {
      const res = await serverApi.createInvite(server.id, {});
      const inviteData = res.data?.data || res.data;
      if (inviteData && inviteData.code) {
        setInvite(inviteData);
        message.success('Tạo mã mời Server thành công!');
      } else {
        throw new Error('Dữ liệu mã mời trả về không hợp lệ');
      }
    } catch (err) {
      const msg = getInviteErrorMessage(err);
      setErrorMsg(msg);
      message.error(msg);
    } finally {
      setLoading(false);
      submittingRef.current = false;
    }
  };

  const handleCopyCode = async () => {
    if (!invite?.code) return;

    const result = await copyToClipboard(invite.code);
    if (result.success) {
      setCopied(true);
      message.success('Đã sao chép mã mời vào clipboard!');
      setTimeout(() => {
        setCopied(false);
      }, 2500);
    } else {
      message.warning(result.error || 'Vui lòng bôi đen và sao chép mã mời thủ công.');
    }
  };

  return (
    <Modal
      open={open}
      onCancel={handleClose}
      destroyOnClose
      footer={null}
      closable={false}
      width={480}
      centered
      styles={{
        body: { padding: 0 },
        mask: { backdropFilter: 'blur(8px)', background: 'rgba(0, 0, 0, 0.75)' },
      }}
    >
      <div
        className="glass-panel group-invite-modal"
        style={{
          borderRadius: 20,
          background: 'var(--color-bg-surface, #141B2D)',
          border: '1px solid var(--border-subtle, rgba(255, 255, 255, 0.08))',
          boxShadow: '0 24px 48px rgba(0, 0, 0, 0.6), 0 0 24px rgba(0, 242, 254, 0.1)',
          overflow: 'hidden',
          position: 'relative',
        }}
      >
        {/* Nút đóng góc phải */}
        <Button
          type="text"
          icon={<CloseOutlined style={{ fontSize: 16, color: '#94A3B8' }} />}
          onClick={handleClose}
          disabled={loading}
          aria-label="Đóng modal"
          style={{
            position: 'absolute',
            top: 16,
            right: 16,
            zIndex: 10,
            width: 32,
            height: 32,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: '50%',
            background: 'rgba(255, 255, 255, 0.04)',
          }}
        />

        {/* Modal Header */}
        <div
          style={{
            padding: '28px 28px 20px',
            borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
            background: 'linear-gradient(180deg, rgba(0, 242, 254, 0.06) 0%, transparent 100%)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <Avatar
              shape="square"
              src={server?.iconUrl}
              size={48}
              style={{
                borderRadius: 14,
                background: 'linear-gradient(135deg, #00f2fe 0%, #4facfe 100%)',
                color: '#0b1120',
                fontWeight: 700,
                fontSize: 20,
                flexShrink: 0,
                border: '1px solid rgba(0, 242, 254, 0.3)',
              }}
            >
              {server?.name?.[0]?.toUpperCase() || 'S'}
            </Avatar>
            <div style={{ minWidth: 0, flex: 1 }}>
              <Title
                level={4}
                style={{
                  margin: 0,
                  color: '#F8FAFC',
                  fontSize: 18,
                  fontWeight: 700,
                  letterSpacing: '-0.3px',
                }}
              >
                Mời bạn bè vào Server
              </Title>
              <Text
                style={{
                  color: '#94A3B8',
                  fontSize: 13,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'block',
                  marginTop: 2,
                }}
              >
                Không gian: <strong style={{ color: '#00f2fe' }}>{server?.name || 'Server'}</strong>
              </Text>
            </div>
          </div>
        </div>

        {/* Modal Body */}
        <div style={{ padding: '24px 28px 28px' }}>
          {/* Thông báo lỗi nếu có */}
          {errorMsg && (
            <Alert
              type="error"
              showIcon
              message={errorMsg}
              style={{
                marginBottom: 20,
                borderRadius: 10,
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.25)',
                color: '#fca5a5',
              }}
              action={
                <Button
                  type="link"
                  size="small"
                  icon={<ReloadOutlined />}
                  onClick={handleCreateInvite}
                  loading={loading}
                  style={{ color: '#f87171', padding: 0 }}
                >
                  Thử lại
                </Button>
              }
            />
          )}

          {/* Chưa tạo mã mời */}
          {!invite && (
            <div style={{ textAlign: 'center', padding: '16px 0 8px' }}>
              <div
                style={{
                  width: 64,
                  height: 64,
                  borderRadius: 20,
                  background: 'rgba(0, 242, 254, 0.08)',
                  border: '1px solid rgba(0, 242, 254, 0.2)',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: 16,
                  color: '#00f2fe',
                  fontSize: 28,
                  boxShadow: '0 8px 24px rgba(0, 242, 254, 0.12)',
                }}
              >
                <ShareAltOutlined />
              </div>
              <Paragraph style={{ color: '#94A3B8', fontSize: 13, lineHeight: 1.7, margin: '0 auto 24px', maxWidth: 360 }}>
                Nhấn vào nút bên dưới để hệ thống sinh một mã mời ngẫu nhiên an toàn. Người nhận có thể dùng mã này để tham gia ngay vào Server của bạn.
              </Paragraph>

              <Button
                type="primary"
                size="large"
                icon={<UserAddOutlined />}
                loading={loading}
                onClick={handleCreateInvite}
                style={{
                  width: '100%',
                  height: 48,
                  borderRadius: 12,
                  background: 'linear-gradient(135deg, #00f2fe 0%, #4facfe 100%)',
                  border: 'none',
                  color: '#0B1120',
                  fontWeight: 700,
                  fontSize: 15,
                  boxShadow: '0 4px 16px rgba(0, 242, 254, 0.3)',
                }}
              >
                {loading ? 'Đang tạo mã mời…' : 'Tạo mã mời Server'}
              </Button>
            </div>
          )}

          {/* Đã tạo mã mời thành công */}
          {invite && (
            <div>
              <Text style={{ display: 'block', color: '#94A3B8', fontSize: 12, marginBottom: 8, fontWeight: 600 }}>
                MÃ MỜI THAM GIA SERVER
              </Text>

              {/* Hộp hiển thị mã mời */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '12px 16px',
                  borderRadius: 12,
                  background: 'rgba(11, 17, 32, 0.8)',
                  border: copied ? '1px solid #10B981' : '1px solid rgba(0, 242, 254, 0.3)',
                  boxShadow: copied ? '0 0 16px rgba(16, 185, 129, 0.2)' : '0 0 16px rgba(0, 242, 254, 0.1)',
                  transition: 'all 0.2s ease',
                  marginBottom: 16,
                }}
              >
                <span
                  style={{
                    fontFamily: 'monospace, "SF Mono", Consolas',
                    fontSize: 22,
                    fontWeight: 700,
                    letterSpacing: '3px',
                    color: copied ? '#34D399' : '#00f2fe',
                    flex: 1,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    userSelect: 'all',
                  }}
                  title={invite.code}
                >
                  {invite.code}
                </span>

                <Tooltip title={copied ? 'Đã sao chép' : 'Sao chép mã'}>
                  <Button
                    type={copied ? 'primary' : 'default'}
                    icon={copied ? <CheckOutlined /> : <CopyOutlined />}
                    onClick={handleCopyCode}
                    style={{
                      height: 40,
                      borderRadius: 10,
                      fontWeight: 600,
                      background: copied ? '#10B981' : 'rgba(255, 255, 255, 0.08)',
                      borderColor: copied ? '#10B981' : 'rgba(255, 255, 255, 0.15)',
                      color: copied ? '#FFFFFF' : '#E2E8F0',
                    }}
                  >
                    {copied ? 'Đã chép' : 'Sao chép'}
                  </Button>
                </Tooltip>
              </div>

              {/* Thông tin metadata */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '10px 14px',
                  borderRadius: 8,
                  background: 'rgba(255, 255, 255, 0.02)',
                  border: '1px solid rgba(255, 255, 255, 0.05)',
                  marginBottom: 24,
                  fontSize: 12,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#94A3B8' }}>
                  <ClockCircleOutlined style={{ color: '#00f2fe' }} />
                  <span>Thời hạn:</span>
                  <strong style={{ color: '#E2E8F0' }}>{formatExpiresAt(invite.expiresAt)}</strong>
                </div>

                <Tag color="cyan" style={{ margin: 0, borderRadius: 6, fontWeight: 600 }}>
                  {invite.status || 'ACTIVE'}
                </Tag>
              </div>

              {/* Nút tạo mã khác hoặc đóng */}
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Button
                  type="text"
                  icon={<ReloadOutlined />}
                  loading={loading}
                  onClick={handleCreateInvite}
                  style={{
                    color: '#94A3B8',
                    fontSize: 13,
                    borderRadius: 8,
                    padding: '4px 8px',
                  }}
                >
                  Tạo mã mới khác
                </Button>

                <Button
                  onClick={handleClose}
                  style={{
                    borderRadius: 8,
                    background: 'rgba(255, 255, 255, 0.06)',
                    borderColor: 'rgba(255, 255, 255, 0.12)',
                    color: '#E2E8F0',
                  }}
                >
                  Xong
                </Button>
              </Space>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
