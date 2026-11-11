import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Modal, Space, Typography, message } from 'antd';
import { DeleteOutlined, ExclamationCircleOutlined, CloseOutlined } from '@ant-design/icons';
import channelApi from '../../api/channelApi';
import { canDeleteChannel, getChannelDeleteErrorMessage } from '../../utils/channelUtils';

const { Title, Text } = Typography;

export default function DeleteChannelModal({
  open,
  channel,
  totalChannelsCount,
  onClose,
  onSuccess,
}) {
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const submittingRef = useRef(false);

  useEffect(() => {
    if (open) {
      setErrorMsg(null);
      submittingRef.current = false;
    }
  }, [open, channel]);

  if (!channel) return null;

  const { canDelete, reason } = canDeleteChannel(channel, totalChannelsCount);

  const handleDelete = async () => {
    if (submittingRef.current || loading || !canDelete) return;

    submittingRef.current = true;
    setLoading(true);
    setErrorMsg(null);

    try {
      await channelApi.delete(channel.id);
      message.success(`Đã xóa kênh #${channel.name} thành công!`);
      onSuccess?.(channel.id);
      onClose();
    } catch (err) {
      setErrorMsg(getChannelDeleteErrorMessage(err, channel.name));
    } finally {
      setLoading(false);
      submittingRef.current = false;
    }
  };

  return (
    <Modal
      open={open}
      onCancel={() => {
        if (!loading) onClose();
      }}
      footer={null}
      centered
      width={440}
      destroyOnClose
      maskClosable={!loading}
      styles={{
        content: {
          background: 'linear-gradient(145deg, #161828 0%, #0d0e18 100%)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          borderRadius: 16,
          boxShadow: '0 16px 40px rgba(0, 0, 0, 0.6), 0 0 20px rgba(239, 68, 68, 0.15)',
          padding: 24,
        },
        mask: {
          backdropFilter: 'blur(5px)',
          background: 'rgba(5, 7, 15, 0.75)',
        },
      }}
    >
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <div
          style={{
            width: 52,
            height: 52,
            margin: '0 auto 12px',
            borderRadius: 14,
            background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.25) 0%, rgba(220, 38, 38, 0.15) 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 24,
            color: '#f87171',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            boxShadow: '0 0 15px rgba(239, 68, 68, 0.2)',
          }}
        >
          <DeleteOutlined />
        </div>
        <Title level={4} style={{ color: '#fff', margin: 0, fontWeight: 700 }}>
          Xóa Kênh Trò Chuyện
        </Title>
        <Text style={{ color: '#94a3b8', fontSize: 13, display: 'block', marginTop: 4 }}>
          Bạn có chắc chắn muốn xóa kênh <strong style={{ color: '#00f2fe' }}>#{channel.name}</strong>?
        </Text>
      </div>

      {!canDelete ? (
        <Alert
          message={reason}
          type="warning"
          showIcon
          style={{
            marginBottom: 20,
            background: 'rgba(245, 158, 11, 0.1)',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            borderRadius: 8,
            color: '#fde68a',
          }}
        />
      ) : (
        <Alert
          message="Hành động này không thể hoàn tác!"
          description="Toàn bộ tin nhắn và dữ liệu trao đổi trong kênh này sẽ bị xóa vĩnh viễn khỏi hệ thống."
          type="error"
          showIcon
          icon={<ExclamationCircleOutlined />}
          style={{
            marginBottom: 20,
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: 8,
            color: '#fca5a5',
          }}
        />
      )}

      {errorMsg && (
        <Alert
          message={errorMsg}
          type="error"
          showIcon
          style={{
            marginBottom: 16,
            background: 'rgba(239, 68, 68, 0.15)',
            border: '1px solid rgba(239, 68, 68, 0.35)',
            borderRadius: 8,
            color: '#fca5a5',
          }}
        />
      )}

      <div style={{ marginTop: 24 }}>
        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
          <Button
            onClick={onClose}
            disabled={loading}
            icon={<CloseOutlined />}
            style={{
              borderRadius: 8,
              background: 'rgba(255, 255, 255, 0.05)',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#94a3b8',
            }}
          >
            Hủy bỏ
          </Button>
          <Button
            type="primary"
            danger
            loading={loading}
            disabled={!canDelete}
            icon={<DeleteOutlined />}
            onClick={handleDelete}
            style={{
              borderRadius: 8,
              fontWeight: 600,
              boxShadow: canDelete ? '0 4px 14px rgba(239, 68, 68, 0.35)' : 'none',
            }}
          >
            Xác nhận xóa
          </Button>
        </Space>
      </div>
    </Modal>
  );
}
