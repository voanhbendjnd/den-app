import React, { useState, useRef } from 'react';
import { Avatar, Input, Button, Select, Tooltip, message, Spin, Typography } from 'antd';
import {
  PictureOutlined,
  SendOutlined,
  GlobalOutlined,
  TeamOutlined,
  LockOutlined,
  CloseCircleFilled,
  UserOutlined,
  ThunderboltFilled,
} from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import fileApi from '../../api/fileApi';

const { Text } = Typography;
const MAX_CONTENT_LENGTH = 120;

export default function CreatePostCard({ onSubmit, disabled = false }) {
  const { currentUser } = useAuth();
  const [content, setContent] = useState('');
  const [privacy, setPrivacy] = useState('PUBLIC');
  const [mediaUrl, setMediaUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fileInputRef = useRef(null);

  const displayName = currentUser?.displayName || currentUser?.username || currentUser?.name || 'Bạn';
  const avatarUrl = currentUser?.avatar;

  // Handle file selection and upload
  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      message.error('Vui lòng chọn file hình ảnh (JPG, PNG, WebP, GIF)');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      message.error('Dung lượng ảnh tối đa là 5MB');
      return;
    }

    try {
      setUploading(true);
      const res = await fileApi.upload(file, 'posts');
      const data = res.data?.data ?? res.data ?? {};
      const uploadedUrl = data.url || (data.fileName ? fileApi.getFileUrl('posts', data.fileName) : '');

      if (uploadedUrl) {
        setMediaUrl(uploadedUrl);
        message.success('Tải ảnh lên thành công!');
      } else {
        message.warning('Không tìm thấy đường dẫn ảnh sau khi tải lên.');
      }
    } catch (err) {
      console.error('[CreatePostCard] Upload failed:', err);
      message.error(err.response?.data?.message || 'Không thể tải ảnh lên. Vui lòng thử lại sau.');
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleRemoveImage = () => {
    setMediaUrl('');
  };

  const canSubmit = (content.trim().length > 0 || Boolean(mediaUrl)) && !uploading && !submitting && !disabled;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    if (content.trim().length > MAX_CONTENT_LENGTH) {
      message.error(`Nội dung không được vượt quá ${MAX_CONTENT_LENGTH} ký tự!`);
      return;
    }

    try {
      setSubmitting(true);
      await onSubmit?.({
        content: content.trim(),
        privacy,
        mediaUrl: mediaUrl || null,
      });

      // Reset form upon successful submit
      setContent('');
      setMediaUrl('');
      message.success('Đã đăng bài viết mới thành công! 🎉');
    } catch (err) {
      console.error('[CreatePostCard] Submit failed:', err);
      message.error(err.message || 'Không thể đăng bài viết. Vui lòng thử lại sau.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const charCount = content.length;
  const isNearLimit = charCount >= 100;
  const isOverLimit = charCount > MAX_CONTENT_LENGTH;

  return (
    <div
      className="glass-card"
      style={{
        background: 'linear-gradient(145deg, rgba(22, 30, 46, 0.9) 0%, rgba(15, 23, 42, 0.95) 100%)',
        border: '1px solid rgba(99, 102, 241, 0.25)',
        borderRadius: 18,
        padding: '20px 22px',
        marginBottom: 24,
        boxShadow: '0 8px 30px rgba(0, 0, 0, 0.35)',
        transition: 'all 0.2s ease',
      }}
    >
      {/* Top Header: Avatar + User Info + Privacy Selector */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Avatar
            size={44}
            src={avatarUrl}
            icon={!avatarUrl && <UserOutlined />}
            style={{
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              fontWeight: 700,
              boxShadow: '0 2px 10px rgba(99, 102, 241, 0.4)',
              flexShrink: 0,
            }}
          >
            {displayName.charAt(0).toUpperCase()}
          </Avatar>
          <div>
            <Text style={{ color: '#F1F5F9', fontWeight: 700, fontSize: 14.5, display: 'block' }}>
              {displayName}
            </Text>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }}>
              <ThunderboltFilled style={{ color: '#00F2FE', fontSize: 11 }} />
              <Text style={{ color: '#64748B', fontSize: 11.5 }}>Tạo bài viết mới</Text>
            </div>
          </div>
        </div>

        {/* Privacy Dropdown Selector */}
        <Select
          value={privacy}
          onChange={(val) => setPrivacy(val)}
          style={{ width: 140 }}
          dropdownStyle={{
            background: '#161E2E',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            borderRadius: 10,
          }}
          options={[
            {
              value: 'PUBLIC',
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: '#00F2FE' }}>
                  <GlobalOutlined /> Công khai
                </span>
              ),
            },
            {
              value: 'FRIENDS_ONLY',
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: '#818CF8' }}>
                  <TeamOutlined /> Bạn bè
                </span>
              ),
            },
            {
              value: 'PRIVATE',
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: '#F87171' }}>
                  <LockOutlined /> Chỉ mình tôi
                </span>
              ),
            },
          ]}
        />
      </div>

      {/* Input Textarea Area */}
      <div style={{ position: 'relative', marginBottom: 12 }}>
        <Input.TextArea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          maxLength={MAX_CONTENT_LENGTH}
          placeholder="Bạn đang nghĩ gì thế? Chia sẻ khoảnh khắc với bạn bè ngay..."
          autoSize={{ minRows: 2, maxRows: 6 }}
          style={{
            background: 'rgba(15, 23, 42, 0.65)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: 12,
            color: '#F1F5F9',
            fontSize: 14.5,
            padding: '12px 14px',
            resize: 'none',
            boxShadow: 'inset 0 2px 4px rgba(0, 0, 0, 0.3)',
          }}
        />

        {/* Real-time Character Counter */}
        <div
          style={{
            position: 'absolute',
            bottom: 8,
            right: 12,
            fontSize: 11,
            fontWeight: 600,
            color: isOverLimit ? '#EF4444' : isNearLimit ? '#F59E0B' : '#64748B',
            pointerEvents: 'none',
          }}
        >
          {charCount}/{MAX_CONTENT_LENGTH}
        </div>
      </div>

      {/* Media Attachment Preview (if uploaded) */}
      {mediaUrl && (
        <div
          style={{
            position: 'relative',
            marginBottom: 16,
            borderRadius: 12,
            overflow: 'hidden',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            maxHeight: 280,
            background: 'rgba(0, 0, 0, 0.4)',
          }}
        >
          <img
            src={mediaUrl}
            alt="Preview attachment"
            style={{
              width: '100%',
              maxHeight: 280,
              objectFit: 'cover',
              display: 'block',
            }}
          />
          <Tooltip title="Xóa ảnh">
            <Button
              type="text"
              icon={<CloseCircleFilled style={{ fontSize: 20, color: '#F87171' }} />}
              onClick={handleRemoveImage}
              style={{
                position: 'absolute',
                top: 8,
                right: 8,
                background: 'rgba(15, 23, 42, 0.8)',
                borderRadius: '50%',
                width: 32,
                height: 32,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 0,
              }}
            />
          </Tooltip>
        </div>
      )}

      {/* Hidden file input for uploading images */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        style={{ display: 'none' }}
      />

      {/* Bottom Action Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingTop: 12,
          borderTop: '1px solid rgba(255, 255, 255, 0.07)',
          flexWrap: 'wrap',
          gap: 10,
        }}
      >
        {/* Left Action: Upload Image Button */}
        <Button
          type="text"
          icon={uploading ? <Spin size="small" /> : <PictureOutlined style={{ color: '#00F2FE', fontSize: 16 }} />}
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading || Boolean(mediaUrl)}
          style={{
            color: '#94A3B8',
            fontWeight: 600,
            fontSize: 13,
            borderRadius: 8,
            padding: '4px 12px',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            background: 'rgba(255, 255, 255, 0.04)',
            border: '1px solid rgba(255, 255, 255, 0.08)',
          }}
        >
          {uploading ? 'Đang tải ảnh...' : mediaUrl ? 'Đã đính kèm ảnh' : 'Đính kèm ảnh'}
        </Button>

        {/* Right Action: Ctrl+Enter hint & Submit Button */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Text style={{ color: '#64748B', fontSize: 11.5, display: 'none', '@media (min-width: 600px)': { display: 'inline' } }}>
            Ctrl + Enter
          </Text>

          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={submitting}
            disabled={!canSubmit}
            onClick={handleSubmit}
            style={{
              height: 38,
              padding: '0 22px',
              borderRadius: 10,
              fontWeight: 700,
              fontSize: 13.5,
              background: canSubmit ? 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)' : undefined,
              border: 'none',
              boxShadow: canSubmit ? '0 4px 14px rgba(99, 102, 241, 0.4)' : undefined,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              transition: 'all 0.2s ease',
            }}
          >
            Đăng bài
          </Button>
        </div>
      </div>
    </div>
  );
}
