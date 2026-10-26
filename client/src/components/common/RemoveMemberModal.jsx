import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Modal, Typography, message } from 'antd';
import { UserDeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import serverApi from '../../api/serverApi';
import { canRemoveMember, getRemoveMemberErrorMessage } from '../../utils/serverMember';

const { Title, Text } = Typography;

export default function RemoveMemberModal({
  open,
  server,
  currentUser,
  member,
  onClose,
  onSuccess,
}) {
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const submittingRef = useRef(false);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    if (open) {
      setErrorMsg(null);
      submittingRef.current = false;
    }
    return () => {
      isMountedRef.current = false;
    };
  }, [open, member]);

  if (!server || !member) return null;

  const { canRemove, reason } = canRemoveMember(server, currentUser, member);
  const displayName = member.nickname || member.name || member.username || 'Thành viên';

  const handleConfirm = async () => {
    if (submittingRef.current || loading || !canRemove) return;

    submittingRef.current = true;
    setLoading(true);
    setErrorMsg(null);

    try {
      await serverApi.removeMember(server.id, member.userId);
      message.success(`Đã loại "${displayName}" khỏi Server thành công!`);
      if (onSuccess) {
        onSuccess(member.userId);
      }
      if (isMountedRef.current) {
        onClose();
      }
    } catch (err) {
      if (isMountedRef.current) {
        setErrorMsg(getRemoveMemberErrorMessage(err, displayName));
      }
    } finally {
      if (isMountedRef.current) {
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
            color: '#ef4444',
            border: '1px solid rgba(239, 68, 68, 0.35)',
          }}
        >
          <UserDeleteOutlined />
        </div>
        <Title level={4} style={{ color: '#f87171', margin: 0, fontWeight: 600 }}>
          Loại thành viên khỏi Server
        </Title>
        <Text style={{ color: '#94a3b8', fontSize: 13, marginTop: 4, display: 'block' }}>
          {server.name}
        </Text>
      </div>

      {errorMsg && (
        <Alert
          message={errorMsg}
          type="error"
          showIcon
          icon={<ExclamationCircleOutlined />}
          style={{
            marginBottom: 16,
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: 8,
            color: '#fca5a5',
          }}
        />
      )}

      {!canRemove ? (
        <Alert
          message={reason || 'Bạn không thể loại thành viên này.'}
          type="warning"
          showIcon
          style={{
            marginBottom: 20,
            background: 'rgba(245, 158, 11, 0.12)',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            borderRadius: 8,
            color: '#fde68a',
          }}
        />
      ) : (
        <div
          style={{
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            borderRadius: 10,
            padding: '14px 16px',
            marginBottom: 20,
            fontSize: 13,
            lineHeight: 1.6,
            color: '#cbd5e1',
          }}
        >
          Bạn có chắc chắn muốn loại <strong style={{ color: '#fff' }}>{displayName}</strong>
          {member.username && member.username !== displayName && (
            <span style={{ color: '#94a3b8' }}> (@{member.username})</span>
          )}{' '}
          khỏi Server <strong style={{ color: '#00f2fe' }}>{server.name}</strong> không?
          <div style={{ marginTop: 8, color: '#f87171', fontSize: 12 }}>
            * Người này sẽ mất toàn bộ quyền truy cập vào các kênh và tin nhắn trong Server.
          </div>
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
        <Button
          onClick={onClose}
          disabled={loading}
          style={{
            background: 'rgba(255, 255, 255, 0.06)',
            borderColor: 'rgba(255, 255, 255, 0.15)',
            color: '#cbd5e1',
            borderRadius: 8,
            height: 38,
            padding: '0 18px',
          }}
        >
          Hủy
        </Button>
        {canRemove && (
          <Button
            type="primary"
            danger
            loading={loading}
            onClick={handleConfirm}
            style={{
              background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
              borderColor: '#ef4444',
              borderRadius: 8,
              height: 38,
              padding: '0 20px',
              fontWeight: 500,
              boxShadow: '0 4px 12px rgba(239, 68, 68, 0.35)',
            }}
          >
            {loading ? 'Đang loại...' : 'Xác nhận loại'}
          </Button>
        )}
      </div>
    </Modal>
  );
}
