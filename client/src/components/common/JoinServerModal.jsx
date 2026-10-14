import { useState, useRef, useEffect } from 'react';
import { Modal, Form, Input, Button, Typography, message } from 'antd';
import {
  CompassOutlined,
  CloseOutlined,
  QrcodeOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import serverApi from '../../api/serverApi';
import { validateJoinInput, parseJoinInput, getJoinErrorMessage } from '../../utils/serverJoin';

const { Title, Text } = Typography;

export default function JoinServerModal({ open, onClose, onSuccess }) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const submittingRef = useRef(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      submittingRef.current = false;
    }
  }, [open, form]);

  const handleClose = () => {
    if (loading) return;
    form.resetFields();
    submittingRef.current = false;
    onClose();
  };

  const handleSubmit = async (values) => {
    if (submittingRef.current || loading) return;

    const rawInput = values.serverInput || values.serverId;
    const validationErr = validateJoinInput(rawInput);
    if (validationErr) {
      message.error(validationErr);
      return;
    }

    const parsed = parseJoinInput(rawInput);
    if (!parsed) {
      message.error('Mã mời hoặc Server ID không hợp lệ.');
      return;
    }

    submittingRef.current = true;
    setLoading(true);

    try {
      let joinedServer = null;
      if (parsed.type === 'inviteCode') {
        const response = await serverApi.joinByInvite(parsed.value);
        joinedServer = response?.data?.data || response?.data;
      } else {
        const response = await serverApi.join(parsed.value);
        joinedServer = response?.data?.data || response?.data;
      }

      message.success('Tham gia không gian Server thành công!');
      const targetServerId = joinedServer?.id || (parsed.type === 'serverId' ? parsed.value : null);
      if (onSuccess) {
        onSuccess(targetServerId);
      }
      handleClose();
    } catch (err) {
      const errorMsg = getJoinErrorMessage(err);
      message.error(errorMsg);
    } finally {
      setLoading(false);
      submittingRef.current = false;
    }
  };

  return (
    <Modal
      open={open}
      onCancel={handleClose}
      destroyOnClose
      footer={null}
      closable={false}
      width={460}
      centered
      styles={{
        body: { padding: 0 },
        mask: { backdropFilter: 'blur(8px)', background: 'rgba(0, 0, 0, 0.75)' },
      }}
    >
      <div
        className="glass-panel"
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
          style={{
            position: 'absolute',
            top: 16,
            right: 16,
            width: 32,
            height: 32,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 8,
            zIndex: 10,
          }}
        />

        {/* Header Modal */}
        <div
          style={{
            padding: '28px 28px 16px',
            textAlign: 'center',
            borderBottom: '1px solid var(--border-subtle, rgba(255, 255, 255, 0.06))',
          }}
        >
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: 16,
              background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(0, 242, 254, 0.2) 100%)',
              border: '1px solid rgba(0, 242, 254, 0.3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 14px',
              boxShadow: '0 0 16px rgba(0, 242, 254, 0.2)',
            }}
          >
            <CompassOutlined style={{ fontSize: 26, color: '#00F2FE' }} />
          </div>
          <Title level={3} style={{ margin: '0 0 6px', color: '#F1F5F9', fontWeight: 700, fontSize: 20 }}>
            Tham Gia Server
          </Title>
          <Text style={{ color: '#94A3B8', fontSize: 13, display: 'block', maxWidth: 360, margin: '0 auto' }}>
            Nhập mã mời hoặc ID của Server bạn muốn tham gia để bắt đầu kết nối.
          </Text>
        </div>

        {/* Form Body */}
        <div style={{ padding: '24px 28px 28px' }}>
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            requiredMark={false}
          >
            <Form.Item
              name="serverInput"
              label={
                <Text style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
                  MÃ MỜI HOẶC SERVER ID <span style={{ color: '#F43F5E' }}>*</span>
                </Text>
              }
              rules={[
                {
                  validator: (_, value) => {
                    const err = validateJoinInput(value);
                    if (err) return Promise.reject(new Error(err));
                    return Promise.resolve();
                  },
                },
              ]}
              style={{ marginBottom: 24 }}
            >
              <Input
                prefix={<QrcodeOutlined style={{ color: '#64748B', marginRight: 6 }} />}
                placeholder="Ví dụ: aB9xK2Lm hoặc 10"
                disabled={loading}
                autoFocus
                size="large"
                style={{
                  height: 46,
                  borderRadius: 10,
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid var(--border-subtle, rgba(255, 255, 255, 0.12))',
                  color: '#F8FAFC',
                  fontSize: 15,
                }}
              />
            </Form.Item>

            {/* Hint Box */}
            <div
              style={{
                padding: '12px 14px',
                borderRadius: 8,
                background: 'rgba(99, 102, 241, 0.08)',
                border: '1px solid rgba(99, 102, 241, 0.2)',
                marginBottom: 24,
                display: 'flex',
                gap: 10,
                alignItems: 'flex-start',
              }}
            >
              <Text style={{ color: '#818CF8', fontSize: 13, lineHeight: '18px' }}>
                💡 <strong>Mẹo:</strong> Bạn có thể dán liên kết mời hoặc mã mời gồm 8 ký tự (ví dụ: <code style={{ color: '#00F2FE' }}>aB9xK2Lm</code>) để tham gia Server.
              </Text>
            </div>

            {/* Actions */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
              <Button
                onClick={handleClose}
                disabled={loading}
                style={{
                  height: 42,
                  padding: '0 20px',
                  borderRadius: 10,
                  border: '1px solid rgba(255, 255, 255, 0.1)',
                  background: 'transparent',
                  color: '#94A3B8',
                  fontWeight: 600,
                }}
              >
                Hủy
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                icon={loading ? <LoadingOutlined /> : <CompassOutlined />}
                style={{
                  height: 42,
                  padding: '0 24px',
                  borderRadius: 10,
                  border: 'none',
                  background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                  boxShadow: '0 4px 16px rgba(0, 242, 254, 0.35)',
                  fontWeight: 700,
                  color: '#0F172A',
                }}
              >
                {loading ? 'Đang Tham Gia...' : 'Tham Gia Server'}
              </Button>
            </div>
          </Form>
        </div>
      </div>
    </Modal>
  );
}
