import { useState, useEffect } from 'react';
import { Modal, Form, Input, Button, Typography, Alert, message, Space } from 'antd';
import {
  LockOutlined,
  KeyOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import userApi from '../../api/userApi';
import {
  validatePasswordChange,
  getPasswordChangeErrorMessage,
} from '../../utils/profileUtils';

const { Title, Text } = Typography;

export default function ChangePasswordModal({
  open,
  onClose,
  onSuccess,
}) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);

  // Reset form khi modal mở hoặc đóng
  useEffect(() => {
    if (open) {
      setErrorMsg(null);
      form.resetFields();
    }
  }, [open, form]);

  const handleSubmit = async (values) => {
    // Client-side validation kiểm tra trước
    const validation = validatePasswordChange(values);
    if (!validation.isValid) {
      const firstErr = Object.values(validation.errors)[0];
      setErrorMsg(firstErr);
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    const payload = {
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    };

    try {
      await userApi.changePassword(payload);
      message.success('Đổi mật khẩu thành công!');
      form.resetFields();
      onSuccess?.();
      onClose();
    } catch (err) {
      const msg = getPasswordChangeErrorMessage(err);
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={loading ? undefined : onClose}
      footer={null}
      centered
      width={480}
      destroyOnClose
      maskClosable={!loading}
      styles={{
        content: {
          background: 'linear-gradient(145deg, #161C2C 0%, #0D121F 100%)',
          borderRadius: 18,
          border: '1px solid rgba(99, 102, 241, 0.25)',
          boxShadow: '0 24px 60px rgba(0, 0, 0, 0.65), 0 0 30px rgba(0, 242, 254, 0.08)',
          padding: 28,
        },
      }}
    >
      {/* Modal Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 12,
              background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(0, 242, 254, 0.2) 100%)',
              border: '1px solid rgba(0, 242, 254, 0.4)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#00F2FE',
              fontSize: 20,
            }}
          >
            <KeyOutlined />
          </div>
          <div>
            <Title level={4} style={{ margin: 0, color: '#F1F5F9', fontWeight: 700 }}>
              Đổi Mật Khẩu Tài Khoản
            </Title>
            <Text style={{ color: '#94A3B8', fontSize: 13 }}>
              Cập nhật mật khẩu mới để bảo vệ tài khoản của bạn.
            </Text>
          </div>
        </div>
      </div>

      {/* Alert Error Box */}
      {errorMsg && (
        <Alert
          type="error"
          showIcon
          message={errorMsg}
          style={{
            marginBottom: 20,
            background: 'rgba(239, 68, 68, 0.1)',
            borderColor: 'rgba(239, 68, 68, 0.3)',
            color: '#FCA5A5',
            borderRadius: 10,
          }}
        />
      )}

      {/* Security Tip Banner */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 10,
          padding: '12px 14px',
          background: 'rgba(15, 23, 42, 0.6)',
          border: '1px solid rgba(255, 255, 255, 0.06)',
          borderRadius: 12,
          marginBottom: 20,
          fontSize: 12,
          color: '#94A3B8',
          lineHeight: 1.5,
        }}
      >
        <InfoCircleOutlined style={{ color: '#6366F1', marginTop: 2, fontSize: 14 }} />
        <div>
          Mật khẩu mới phải có tối thiểu <strong>4 ký tự</strong> và không được trùng với mật khẩu hiện tại.
        </div>
      </div>

      {/* Password Change Form */}
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        requiredMark={false}
      >
        {/* Current Password Field */}
        <Form.Item
          label={
            <span style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
              Mật khẩu hiện tại
            </span>
          }
          name="currentPassword"
          rules={[
            { required: true, message: 'Vui lòng nhập mật khẩu hiện tại' },
          ]}
          style={{ marginBottom: 16 }}
        >
          <Input.Password
            prefix={<LockOutlined style={{ color: '#6366F1' }} />}
            placeholder="Nhập mật khẩu đang sử dụng..."
            style={{
              background: '#0F1523',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#F1F5F9',
              borderRadius: 10,
              height: 42,
            }}
            onChange={() => {
              if (errorMsg) setErrorMsg(null);
            }}
          />
        </Form.Item>

        {/* New Password Field */}
        <Form.Item
          label={
            <span style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
              Mật khẩu mới
            </span>
          }
          name="newPassword"
          rules={[
            { required: true, message: 'Vui lòng nhập mật khẩu mới' },
            { min: 4, message: 'Mật khẩu mới phải có ít nhất 4 ký tự' },
            { max: 100, message: 'Mật khẩu mới không được vượt quá 100 ký tự' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (value && getFieldValue('currentPassword') && value === getFieldValue('currentPassword')) {
                  return Promise.reject(new Error('Mật khẩu mới không được trùng với mật khẩu hiện tại'));
                }
                return Promise.resolve();
              },
            }),
          ]}
          style={{ marginBottom: 16 }}
        >
          <Input.Password
            prefix={<KeyOutlined style={{ color: '#00F2FE' }} />}
            placeholder="Nhập mật khẩu mới (tối thiểu 4 ký tự)..."
            style={{
              background: '#0F1523',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#F1F5F9',
              borderRadius: 10,
              height: 42,
            }}
            onChange={() => {
              if (errorMsg) setErrorMsg(null);
            }}
          />
        </Form.Item>

        {/* Confirm New Password Field */}
        <Form.Item
          label={
            <span style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
              Xác nhận mật khẩu mới
            </span>
          }
          name="confirmPassword"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Vui lòng xác nhận lại mật khẩu mới' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('Mật khẩu xác nhận không khớp với mật khẩu mới'));
              },
            }),
          ]}
          style={{ marginBottom: 26 }}
        >
          <Input.Password
            prefix={<SafetyCertificateOutlined style={{ color: '#10B981' }} />}
            placeholder="Nhập lại mật khẩu mới để xác nhận..."
            style={{
              background: '#0F1523',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#F1F5F9',
              borderRadius: 10,
              height: 42,
            }}
            onChange={() => {
              if (errorMsg) setErrorMsg(null);
            }}
          />
        </Form.Item>

        {/* Action Buttons */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 10 }}>
          <Button
            onClick={onClose}
            disabled={loading}
            style={{
              background: 'rgba(255, 255, 255, 0.05)',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#94A3B8',
              borderRadius: 10,
              fontWeight: 600,
              height: 40,
              padding: '0 20px',
            }}
          >
            Hủy bỏ
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            icon={<SaveOutlined />}
            style={{
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              border: 'none',
              borderRadius: 10,
              fontWeight: 600,
              height: 40,
              padding: '0 24px',
              boxShadow: '0 4px 14px rgba(99, 102, 241, 0.35)',
            }}
          >
            Đổi mật khẩu
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
