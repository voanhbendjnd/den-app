import { useState, useEffect } from 'react';
import { Modal, Form, Input, Button, Typography, Alert, Avatar, Tooltip, message } from 'antd';
import {
  UserOutlined,
  MailOutlined,
  IdcardOutlined,
  PictureOutlined,
  LockOutlined,
  SaveOutlined,
  CloseOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  FireOutlined,
} from '@ant-design/icons';
import userApi from '../../api/userApi';
import { useAuth } from '../../hooks/useAuth';
import {
  validateProfileUpdate,
  sanitizeProfilePayload,
  getProfileUpdateErrorMessage,
  extractProfileData,
  USERNAME_REGEX,
} from '../../utils/profileUtils';

const { Title, Text } = Typography;

// Gợi ý một số Avatar mẫu phong cách Cyber / Sci-fi cho người dùng chọn nhanh
const AVATAR_PRESETS = [
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80',
];

export default function EditProfileModal({
  open,
  onClose,
  onSuccess,
  currentProfile,
}) {
  const [form] = Form.useForm();
  const { updateCurrentUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);
  const [previewAvatar, setPreviewAvatar] = useState('');
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const [previewName, setPreviewName] = useState('');

  // Khi modal mở, nạp dữ liệu hiện tại của user vào form
  useEffect(() => {
    if (open && currentProfile) {
      setErrorMsg(null);
      setAvatarLoadError(false);
      const initialName = currentProfile.name || currentProfile.displayName || '';
      const initialUsername = currentProfile.username || '';
      const initialAvatar = currentProfile.avatar || '';

      setPreviewName(initialName);
      setPreviewAvatar(initialAvatar);

      form.setFieldsValue({
        email: currentProfile.email || '',
        name: initialName,
        username: initialUsername,
        avatar: initialAvatar,
      });
    }
  }, [open, currentProfile, form]);

  // Lắng nghe thay đổi trường avatar để live preview
  const handleAvatarChange = (e) => {
    const val = e.target.value;
    setPreviewAvatar(val);
    setAvatarLoadError(false);
    if (errorMsg) setErrorMsg(null);
  };

  // Lắng nghe thay đổi tên để cập nhật preview chữ cái
  const handleNameChange = (e) => {
    setPreviewName(e.target.value);
    if (errorMsg) setErrorMsg(null);
  };

  // Chọn avatar từ preset
  const handleSelectPreset = (url) => {
    setPreviewAvatar(url);
    setAvatarLoadError(false);
    form.setFieldsValue({ avatar: url });
    if (errorMsg) setErrorMsg(null);
  };

  // Xóa avatar về rỗng
  const handleClearAvatar = () => {
    setPreviewAvatar('');
    setAvatarLoadError(false);
    form.setFieldsValue({ avatar: '' });
    if (errorMsg) setErrorMsg(null);
  };

  // Submit form cập nhật hồ sơ
  const handleSubmit = async (values) => {
    // Client-side validation kiểm tra thêm
    const validation = validateProfileUpdate(values);
    if (!validation.isValid) {
      const firstErr = Object.values(validation.errors)[0];
      setErrorMsg(firstErr);
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    const payload = sanitizeProfilePayload(values);

    try {
      const res = await userApi.updateProfile(payload);
      const updatedData = extractProfileData(res) || res.data?.data || res.data;

      // Cập nhật AuthContext toàn cục để mọi component (Header, Sidebar, ProfilePage) được đồng bộ
      if (typeof updateCurrentUser === 'function') {
        updateCurrentUser({
          id: updatedData.id || currentProfile?.id,
          name: updatedData.name || payload.name,
          displayName: updatedData.name || payload.name,
          username: updatedData.username || payload.username,
          avatar: updatedData.avatar !== undefined ? updatedData.avatar : payload.avatar,
        });
      }

      message.success('Cập nhật hồ sơ cá nhân thành công!');
      onSuccess?.(updatedData);
      onClose();
    } catch (err) {
      const msg = getProfileUpdateErrorMessage(err, values.username);
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  const initialLetter = previewName
    ? previewName.trim().charAt(0).toUpperCase()
    : currentProfile?.name
    ? currentProfile.name.trim().charAt(0).toUpperCase()
    : 'U';

  const showFallbackAvatar = !previewAvatar || avatarLoadError;

  return (
    <Modal
      open={open}
      onCancel={loading ? undefined : onClose}
      footer={null}
      centered
      width={520}
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
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(0, 242, 254, 0.2) 100%)',
              border: '1px solid rgba(0, 242, 254, 0.4)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#00F2FE',
              fontSize: 18,
            }}
          >
            <UserOutlined />
          </div>
          <div>
            <Title level={4} style={{ margin: 0, color: '#F1F5F9', fontWeight: 700 }}>
              Chỉnh Sửa Hồ Sơ Cá Nhân
            </Title>
            <Text style={{ color: '#94A3B8', fontSize: 13 }}>
              Cập nhật tên hiển thị, tên định danh và ảnh đại diện của bạn.
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

      {/* Live Avatar Preview Section */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 18,
          padding: '14px 16px',
          background: 'rgba(15, 23, 42, 0.6)',
          border: '1px solid rgba(255, 255, 255, 0.06)',
          borderRadius: 14,
          marginBottom: 22,
        }}
      >
        <div style={{ position: 'relative' }}>
          <div
            style={{
              padding: 3,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              boxShadow: '0 0 16px rgba(0, 242, 254, 0.25)',
              display: 'inline-block',
            }}
          >
            <Avatar
              size={68}
              src={!showFallbackAvatar ? previewAvatar : undefined}
              onError={() => {
                setAvatarLoadError(true);
                return true;
              }}
              style={{
                background: '#1E293B',
                color: '#F1F5F9',
                fontSize: 26,
                fontWeight: 700,
                border: '2px solid #0F172A',
              }}
            >
              {showFallbackAvatar && initialLetter}
            </Avatar>
          </div>
        </div>

        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
            <span style={{ color: '#F1F5F9', fontWeight: 600, fontSize: 14 }}>
              Xem trước ảnh đại diện
            </span>
            {previewAvatar && !showFallbackAvatar && (
              <CheckCircleOutlined style={{ color: '#10B981', fontSize: 13 }} />
            )}
          </div>
          <div style={{ color: '#64748B', fontSize: 12, marginBottom: 8 }}>
            {showFallbackAvatar
              ? 'Đang sử dụng ảnh đại diện mặc định'
              : 'Đang hiển thị hình ảnh từ liên kết URL'}
          </div>

          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            {previewAvatar && (
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={handleClearAvatar}
                style={{ fontSize: 12, borderRadius: 6 }}
              >
                Gỡ ảnh
              </Button>
            )}

            {/* Presets Button Tooltip */}
            <Tooltip title="Chọn nhanh ảnh phong cách Sci-Fi">
              <span style={{ fontSize: 11, color: '#94A3B8', display: 'flex', alignItems: 'center', gap: 4 }}>
                <FireOutlined style={{ color: '#F59E0B' }} /> Gợi ý:
              </span>
            </Tooltip>
            <div style={{ display: 'flex', gap: 6 }}>
              {AVATAR_PRESETS.map((presetUrl, idx) => (
                <img
                  key={idx}
                  src={presetUrl}
                  alt={`preset-${idx}`}
                  onClick={() => handleSelectPreset(presetUrl)}
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    cursor: 'pointer',
                    border: previewAvatar === presetUrl ? '2px solid #00F2FE' : '1px solid rgba(255, 255, 255, 0.2)',
                    transition: 'all 0.2s',
                  }}
                  title="Nhấn để áp dụng"
                />
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Profile Form */}
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        requiredMark={false}
      >
        {/* Email Field - Disabled */}
        <Form.Item
          label={
            <span style={{ color: '#94A3B8', fontSize: 13, fontWeight: 600 }}>
              Địa chỉ Email <LockOutlined style={{ marginLeft: 4, color: '#64748B' }} />
            </span>
          }
          name="email"
          style={{ marginBottom: 16 }}
        >
          <Input
            prefix={<MailOutlined style={{ color: '#475569' }} />}
            disabled
            style={{
              background: 'rgba(15, 23, 42, 0.5)',
              borderColor: 'rgba(255, 255, 255, 0.05)',
              color: '#64748B',
              borderRadius: 10,
              cursor: 'not-allowed',
            }}
          />
        </Form.Item>

        {/* Name Field */}
        <Form.Item
          label={
            <span style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
              Tên hiển thị (Display Name)
            </span>
          }
          name="name"
          rules={[
            { max: 50, message: 'Tên hiển thị không được vượt quá 50 ký tự' },
            {
              validator: (_, value) => {
                if (value && String(value).trim().length === 0) {
                  return Promise.reject(new Error('Tên hiển thị không được chỉ chứa khoảng trắng'));
                }
                return Promise.resolve();
              },
            },
          ]}
          style={{ marginBottom: 16 }}
        >
          <Input
            prefix={<UserOutlined style={{ color: '#6366F1' }} />}
            placeholder="Nhập tên hiển thị của bạn..."
            maxLength={50}
            showCount
            onChange={handleNameChange}
            style={{
              background: '#0F1523',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#F1F5F9',
              borderRadius: 10,
            }}
          />
        </Form.Item>

        {/* Username Field */}
        <Form.Item
          label={
            <span style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
              Tên người dùng (Username)
            </span>
          }
          name="username"
          rules={[
            { required: true, message: 'Vui lòng nhập tên người dùng' },
            { min: 3, message: 'Tên người dùng phải có ít nhất 3 ký tự' },
            { max: 50, message: 'Tên người dùng không được vượt quá 50 ký tự' },
            {
              pattern: USERNAME_REGEX,
              message: 'Chỉ chấp nhận chữ cái, chữ số, gạch dưới (_), gạch ngang (-) hoặc chấm (.)',
            },
          ]}
          extra={
            <span style={{ color: '#64748B', fontSize: 12 }}>
              Dùng để người khác tìm kiếm và nhắc tên bạn (@username)
            </span>
          }
          style={{ marginBottom: 16 }}
        >
          <Input
            prefix={<span style={{ color: '#00F2FE', fontWeight: 700, marginRight: 2 }}>@</span>}
            placeholder="ví dụ: kyanh_dev"
            maxLength={50}
            showCount
            onChange={() => {
              if (errorMsg) setErrorMsg(null);
            }}
            style={{
              background: '#0F1523',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#F1F5F9',
              borderRadius: 10,
            }}
          />
        </Form.Item>

        {/* Avatar URL Field */}
        <Form.Item
          label={
            <span style={{ color: '#CBD5E1', fontSize: 13, fontWeight: 600 }}>
              Liên kết ảnh đại diện (Avatar URL)
            </span>
          }
          name="avatar"
          rules={[
            { max: 500, message: 'Đường dẫn ảnh không được vượt quá 500 ký tự' },
            {
              validator: (_, value) => {
                if (value && value.trim()) {
                  const trimmed = value.trim();
                  if (!/^https?:\/\/.+/i.test(trimmed) && !/^data:image\/.+/i.test(trimmed)) {
                    return Promise.reject(new Error('Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://'));
                  }
                }
                return Promise.resolve();
              },
            },
          ]}
          style={{ marginBottom: 26 }}
        >
          <Input
            prefix={<PictureOutlined style={{ color: '#F59E0B' }} />}
            placeholder="https://example.com/avatar.png"
            maxLength={500}
            onChange={handleAvatarChange}
            style={{
              background: '#0F1523',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#F1F5F9',
              borderRadius: 10,
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
            Lưu thay đổi
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
