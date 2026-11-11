import { useLayoutEffect, useRef, useState } from 'react';
import { Alert, Button, Modal, Typography, message } from 'antd';
import { LogoutOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import serverApi from '../../api/serverApi';
import { canLeaveServer, getLeaveErrorMessage } from '../../utils/serverLeave';

const { Title, Text } = Typography;

export default function LeaveServerModal({
  open,
  server,
  currentUser,
  onClose,
  onSuccess,
}) {
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const submittingRef = useRef(false);
  const sessionRef = useRef(0);

  useLayoutEffect(() => {
    sessionRef.current += 1;
    setLoading(false);
    if (open) {
      setErrorMsg(null);
      submittingRef.current = false;
    }
    return () => {
      sessionRef.current += 1;
    };
  }, [open, server?.id]);

  if (!server) return null;

  const { canLeave, isOwner, reason } = canLeaveServer(server, currentUser);

  const handleConfirm = async () => {
    if (submittingRef.current || loading || !canLeave) return;

    submittingRef.current = true;
    const session = sessionRef.current;
    setLoading(true);
    setErrorMsg(null);

    try {
      await serverApi.leave(server.id);
      if (sessionRef.current === session) message.success(`Đã rời khỏi Server "${server.name}" thành công!`);
      // Update the Server list even if the original view has unmounted.
      if (onSuccess) {
        onSuccess(server.id);
      }
      if (sessionRef.current === session) {
        onClose();
      }
    } catch (err) {
      if (sessionRef.current === session) {
        setErrorMsg(getLeaveErrorMessage(err, server.name));
      }
    } finally {
      if (sessionRef.current === session) {
        setLoading(false);
        submittingRef.current = false;
      }
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
          <LogoutOutlined />
        </div>
        <Title level={4} style={{ color: '#fff', margin: 0, fontWeight: 700 }}>
          Rời khỏi Server?
        </Title>
        <Text style={{ color: '#94a3b8', fontSize: 13, display: 'block', marginTop: 6 }}>
          Bạn có chắc chắn muốn rời khỏi Server <strong style={{ color: '#00f2fe' }}>{server.name}</strong>?
        </Text>
      </div>

      {isOwner ? (
        <Alert
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          message="Không thể rời Server"
          description={reason || 'Chủ sở hữu không thể tự rời Server. Hãy chuyển quyền sở hữu hoặc xóa Server.'}
          style={{
            marginBottom: 20,
            background: 'rgba(234, 179, 8, 0.1)',
            borderColor: 'rgba(234, 179, 8, 0.3)',
            color: '#fbbf24',
          }}
        />
      ) : (
        <div
          style={{
            background: 'rgba(239, 68, 68, 0.08)',
            border: '1px solid rgba(239, 68, 68, 0.2)',
            borderRadius: 8,
            padding: '12px 16px',
            marginBottom: 20,
            fontSize: 13,
            lineHeight: 1.5,
            color: '#cbd5e1',
          }}
        >
          <Text style={{ color: '#fca5a5', fontWeight: 600, display: 'block', marginBottom: 4 }}>
            Lưu ý quan trọng:
          </Text>
          Bạn sẽ không còn truy cập được các kênh trò chuyện và danh sách thành viên của Server này trừ khi được mời lại.
        </div>
      )}

      {errorMsg && (
        <Alert
          type="error"
          showIcon
          message={errorMsg}
          role="alert"
          style={{
            marginBottom: 20,
            background: 'rgba(239, 68, 68, 0.15)',
            borderColor: 'rgba(239, 68, 68, 0.3)',
            color: '#fca5a5',
          }}
        />
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
        <Button
          onClick={onClose}
          disabled={loading}
          style={{
            borderRadius: 8,
            background: 'rgba(255, 255, 255, 0.05)',
            borderColor: 'rgba(255, 255, 255, 0.1)',
            color: '#94a3b8',
          }}
        >
          Hủy
        </Button>
        {!isOwner && (
          <Button
            type="primary"
            danger
            loading={loading}
            icon={<LogoutOutlined />}
            onClick={handleConfirm}
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
              borderColor: '#ef4444',
              boxShadow: '0 4px 12px rgba(239, 68, 68, 0.3)',
            }}
          >
            Rời Server
          </Button>
        )}
      </div>
    </Modal>
  );
}
