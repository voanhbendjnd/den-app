import React, { useState, useEffect, useRef } from 'react';
import { Modal, Input, Button, Select, Tooltip, message, Spin, Typography, Avatar } from 'antd';
import {
  PictureOutlined,
  GlobalOutlined,
  TeamOutlined,
  LockOutlined,
  CloseCircleFilled,
  UserOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import fileApi from '../../api/fileApi';

const { Text } = Typography;
const MAX_CONTENT_LENGTH = 120;

export default function EditPostModal({ open, post, onCancel, onSubmit }) {
  const [content, setContent] = useState('');
  const [privacy, setPrivacy] = useState('PUBLIC');
  const [mediaUrl, setMediaUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fileInputRef = useRef(null);

  // Đồng bộ dữ liệu bài viết khi mở modal
  useEffect(() => {
    if (post && open) {
      setContent(post.content || '');
      setPrivacy(post.privacy || 'PUBLIC');
      setMediaUrl(post.mediaUrl || '');
    }
  }, [post, open]);

  const authorName = post?.authorName || post?.authorUsername || 'Bạn';
  const authorAvatar = post?.authorAvatar;

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
        message.success('Tải ảnh mới lên thành công!');
      } else {
        message.warning('Không tìm thấy đường dẫn ảnh sau khi tải lên.');
      }
    } catch (err) {
      console.error('[EditPostModal] Upload failed:', err);
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

  const canSave = (content.trim().length > 0 || Boolean(mediaUrl)) && !uploading && !submitting;

  const handleSave = async () => {
    if (!canSave || !post) return;

    if (content.trim().length > MAX_CONTENT_LENGTH) {
      message.error(`Nội dung không được vượt quá ${MAX_CONTENT_LENGTH} ký tự!`);
      return;
    }

    try {
      setSubmitting(true);
      await onSubmit?.(post.id, {
        content: content.trim() || null,
        privacy,
        mediaUrl: mediaUrl || null,
      });
      onCancel?.();
    } catch (err) {
      console.error('[EditPostModal] Submit failed:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const lengthRatio = content.length / MAX_CONTENT_LENGTH;
  const counterColor =
    lengthRatio > 0.9 ? '#EF4444' : lengthRatio > 0.75 ? '#F59E0B' : '#64748B';

  return (
    <Modal
      title={
        <div style={{ color: '#F1F5F9', fontSize: 16, fontWeight: 700 }}>
          Chỉnh sửa bài viết
        </div>
      }
      open={open}
      onCancel={onCancel}
      footer={null}
      destroyOnClose
      centered
      width={520}
      styles={{
        content: {
          background: 'linear-gradient(145deg, rgba(22, 30, 46, 0.95) 0%, rgba(15, 23, 42, 0.98) 100%)',
          border: '1px solid rgba(99, 102, 241, 0.25)',
          borderRadius: 20,
          boxShadow: '0 20px 50px rgba(0, 0, 0, 0.6)',
          backdropFilter: 'blur(16px)',
          padding: '24px',
        },
        header: {
          background: 'transparent',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
          paddingBottom: 14,
          marginBottom: 16,
        },
      }}
    >
      {/* Author and Privacy selector */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Avatar
            size={40}
            src={authorAvatar}
            icon={!authorAvatar && <UserOutlined />}
            style={{
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              fontWeight: 700,
            }}
          >
            {authorName.charAt(0).toUpperCase()}
          </Avatar>
          <div>
            <Text style={{ color: '#F1F5F9', fontWeight: 600, fontSize: 14, display: 'block' }}>
              {authorName}
            </Text>
            <Text style={{ color: '#64748B', fontSize: 12 }}>
              Đang chỉnh sửa bài đăng
            </Text>
          </div>
        </div>

        {/* Privacy Selector */}
        <Select
          value={privacy}
          onChange={setPrivacy}
          size="small"
          style={{ width: 135 }}
          options={[
            {
              value: 'PUBLIC',
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <GlobalOutlined style={{ color: '#00F2FE' }} />
                  <span>Công khai</span>
                </span>
              ),
            },
            {
              value: 'FRIENDS_ONLY',
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <TeamOutlined style={{ color: '#818CF8' }} />
                  <span>Bạn bè</span>
                </span>
              ),
            },
            {
              value: 'PRIVATE',
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <LockOutlined style={{ color: '#F87171' }} />
                  <span>Chỉ mình tôi</span>
                </span>
              ),
            },
          ]}
        />
      </div>

      {/* Textarea content */}
      <div style={{ position: 'relative', marginBottom: 16 }}>
        <Input.TextArea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Bạn đang nghĩ gì?..."
          maxLength={MAX_CONTENT_LENGTH}
          autoSize={{ minRows: 3, maxRows: 6 }}
          style={{
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: 12,
            color: '#F1F5F9',
            fontSize: 14.5,
            padding: '12px 14px',
            resize: 'none',
          }}
        />
        <div
          style={{
            textAlign: 'right',
            marginTop: 6,
            fontSize: 12,
            fontWeight: 600,
            color: counterColor,
          }}
        >
          {content.length}/{MAX_CONTENT_LENGTH}
        </div>
      </div>

      {/* Media Image Preview / Upload Area */}
      {uploading ? (
        <div
          style={{
            padding: '24px',
            textAlign: 'center',
            borderRadius: 12,
            background: 'rgba(0, 0, 0, 0.25)',
            border: '1px dashed rgba(99, 102, 241, 0.4)',
            marginBottom: 16,
          }}
        >
          <Spin size="small" />
          <div style={{ color: '#818CF8', fontSize: 13, marginTop: 8 }}>
            Đang tải ảnh mới lên máy chủ...
          </div>
        </div>
      ) : mediaUrl ? (
        <div
          style={{
            position: 'relative',
            marginBottom: 16,
            borderRadius: 12,
            overflow: 'hidden',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            background: 'rgba(0, 0, 0, 0.4)',
          }}
        >
          <img
            src={mediaUrl}
            alt="Preview"
            style={{
              width: '100%',
              maxHeight: 260,
              objectFit: 'cover',
              display: 'block',
            }}
          />
          <Tooltip title="Gỡ ảnh">
            <Button
              type="text"
              icon={<CloseCircleFilled style={{ color: '#EF4444', fontSize: 20 }} />}
              onClick={handleRemoveImage}
              style={{
                position: 'absolute',
                top: 8,
                right: 8,
                background: 'rgba(15, 23, 42, 0.8)',
                borderRadius: '50%',
                padding: 0,
                width: 28,
                height: 28,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            />
          </Tooltip>
        </div>
      ) : null}

      {/* Footer controls: Add image & Save / Cancel */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingTop: 12,
          borderTop: '1px solid rgba(255, 255, 255, 0.08)',
        }}
      >
        <div>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept="image/*"
            style={{ display: 'none' }}
          />
          <Tooltip title={mediaUrl ? 'Thay đổi ảnh' : 'Thêm ảnh'}>
            <Button
              type="text"
              icon={<PictureOutlined style={{ color: '#00F2FE', fontSize: 18 }} />}
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading || submitting}
              style={{
                color: '#94A3B8',
                borderRadius: 8,
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <span style={{ fontSize: 13 }}>{mediaUrl ? 'Đổi ảnh' : 'Thêm ảnh'}</span>
            </Button>
          </Tooltip>
        </div>

        <div style={{ display: 'flex', gap: 10 }}>
          <Button
            onClick={onCancel}
            disabled={submitting}
            style={{
              borderRadius: 8,
              background: 'transparent',
              borderColor: 'rgba(255, 255, 255, 0.15)',
              color: '#94A3B8',
            }}
          >
            Hủy
          </Button>

          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={submitting}
            disabled={!canSave}
            onClick={handleSave}
            style={{
              background: canSave ? 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)' : undefined,
              borderColor: 'transparent',
              borderRadius: 8,
              fontWeight: 600,
              boxShadow: canSave ? '0 4px 12px rgba(99, 102, 241, 0.35)' : undefined,
            }}
          >
            Lưu thay đổi
          </Button>
        </div>
      </div>
    </Modal>
  );
}
