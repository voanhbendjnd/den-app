import { useState, useRef, useEffect } from 'react';
import { Modal, Form, Input, Button, Typography, message, Avatar, Tooltip, Spin } from 'antd';
import { 
  RocketOutlined, 
  BookOutlined, 
  CodeOutlined, 
  SmileOutlined, 
  CameraOutlined,
  UploadOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import serverApi from '../../api/serverApi';
import fileApi from '../../api/fileApi';
import { useAuth } from '../../hooks/useAuth';

const { Title, Text } = Typography;

// Presets mẫu tinh gọn (chỉ gồm icon và tên, không có chữ mô tả chìm rườm rà)
const TEMPLATE_PRESETS = [
  {
    id: 'custom',
    name: 'Tự tạo',
    defaultName: 'Không Gian Của Tôi',
    iconUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80',
    icon: <RocketOutlined />,
    color: '#6366F1',
  },
  {
    id: 'study',
    name: 'Học tập',
    defaultName: 'Nhóm Học SBA301',
    iconUrl: 'https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=150&auto=format&fit=crop&q=80',
    icon: <BookOutlined />,
    color: '#00F2FE',
  },
  {
    id: 'tech',
    name: 'Lập trình',
    defaultName: 'Tech & Dev Hub',
    iconUrl: 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=150&auto=format&fit=crop&q=80',
    icon: <CodeOutlined />,
    color: '#10B981',
  },
  {
    id: 'community',
    name: 'Giải trí',
    defaultName: 'Góc Trò Chuyện',
    iconUrl: 'https://images.unsplash.com/photo-1511512578047-dfb367046420?w=150&auto=format&fit=crop&q=80',
    icon: <SmileOutlined />,
    color: '#F59E0B',
  },
];

// Bộ icon mẫu có sẵn
const AVATAR_SUGGESTIONS = [
  'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1511512578047-dfb367046420?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=150&auto=format&fit=crop&q=80',
];

export default function CreateServerModal({ open, onClose, onSuccess }) {
  const { currentUser } = useAuth();
  const [form] = Form.useForm();
  const fileInputRef = useRef(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState('custom');
  const [avatarUrl, setAvatarUrl] = useState(AVATAR_SUGGESTIONS[0]);

  // Chọn template mẫu
  const handleSelectTemplate = (tmpl) => {
    setSelectedTemplate(tmpl.id);
    setAvatarUrl(tmpl.iconUrl);
    form.setFieldsValue({
      name: tmpl.defaultName,
      iconUrl: tmpl.iconUrl,
    });
  };

  const handleIconSelect = (url) => {
    setAvatarUrl(url);
    form.setFieldsValue({ iconUrl: url });
  };

  // Chọn file từ máy tính và upload lên server
  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      message.error('Vui lòng chọn file hình ảnh (PNG, JPG, WebP...)');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      message.error('Kích thước ảnh tối đa 5MB');
      return;
    }

    setUploading(true);
    try {
      const res = await fileApi.upload(file, 'servers');
      const uploadedUrl = res.data?.url || res.data?.data?.url;
      if (uploadedUrl) {
        setAvatarUrl(uploadedUrl);
        form.setFieldsValue({ iconUrl: uploadedUrl });
        message.success('Đã tải ảnh lên máy chủ thành công!');
      } else {
        message.error('Không nhận được đường dẫn ảnh từ máy chủ');
      }
    } catch (err) {
      const errMsg = err.response?.data?.message || err.response?.data?.detail || 'Không thể tải ảnh lên. Vui lòng thử lại!';
      message.error(errMsg);
    } finally {
      setUploading(false);
      if (e.target) e.target.value = '';
    }
  };

  // Reset form và khôi phục ảnh đại diện về preset mặc định mỗi khi mở modal
  useEffect(() => {
    if (open) {
      setSelectedTemplate('custom');
      setAvatarUrl(AVATAR_SUGGESTIONS[0]);
      form.resetFields();
      form.setFieldsValue({
        name: TEMPLATE_PRESETS[0].defaultName,
        iconUrl: AVATAR_SUGGESTIONS[0],
      });
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  }, [open, form]);

  const handleClose = () => {
    setSelectedTemplate('custom');
    setAvatarUrl(AVATAR_SUGGESTIONS[0]);
    form.resetFields();
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    onClose();
  };

  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      const res = await serverApi.create({
        name: values.name.trim(),
        iconUrl: values.iconUrl?.trim() || avatarUrl || null,
      });
      message.success('Khởi tạo không gian Server thành công!');
      const serverData = res.data?.data || res.data;
      if (onSuccess) onSuccess(serverData);
      handleClose();
    } catch (err) {
      const fieldError = err.response?.data?.fieldErrors?.[0]?.message;
      const errorMsg = fieldError || err.response?.data?.message || err.response?.data?.detail || 'Không thể tạo server. Vui lòng thử lại!';
      message.error(errorMsg);
    } finally {
      setLoading(false);
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
        mask: { backdropFilter: 'blur(8px)', background: 'rgba(0, 0, 0, 0.75)' }
      }}
      modalRender={(node) => (
        <div style={{
          borderRadius: 20,
          overflow: 'hidden',
          background: 'var(--color-bg-surface)',
          border: '1px solid rgba(99, 102, 241, 0.3)',
          boxShadow: '0 24px 60px rgba(0, 0, 0, 0.8), 0 0 20px rgba(99, 102, 241, 0.25)',
        }}>
          {node}
        </div>
      )}
    >
      {/* Hidden file input */}
      <input 
        type="file" 
        ref={fileInputRef} 
        onChange={handleFileChange} 
        accept="image/*" 
        style={{ display: 'none' }} 
      />

      {/* Modal Header */}
      <div style={{
        padding: '24px 28px 12px',
        textAlign: 'center',
        background: 'linear-gradient(180deg, rgba(99, 102, 241, 0.1) 0%, transparent 100%)',
      }}>
        <Title level={3} style={{ margin: '0 0 4px', color: '#F1F5F9', fontWeight: 800 }}>
          Tạo Không Gian Mới
        </Title>
        <Text style={{ color: '#94A3B8', fontSize: 13 }}>
          Tùy chỉnh biểu tượng và tên gọi cho Server của bạn.
        </Text>
      </div>

      <div style={{ padding: '0 28px 24px' }}>
        {/* Single Clean Avatar Uploader */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', margin: '14px 0 20px' }}>
          <Tooltip title="Bấm vào để chọn ảnh từ máy tính">
            <div 
              onClick={() => fileInputRef.current?.click()}
              style={{
                width: 76,
                height: 76,
                borderRadius: '50%',
                border: '2px dashed #00F2FE',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                position: 'relative',
                overflow: 'hidden',
                background: 'rgba(15, 20, 34, 0.8)',
                boxShadow: '0 0 16px rgba(0, 242, 254, 0.25)',
                transition: 'all 0.25s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = '#6366F1';
                e.currentTarget.style.transform = 'scale(1.04)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = '#00F2FE';
                e.currentTarget.style.transform = 'scale(1)';
              }}
            >
              {uploading ? (
                <div style={{ textAlign: 'center', color: '#00F2FE' }}>
                  <Spin indicator={<LoadingOutlined style={{ fontSize: 22, color: '#00F2FE' }} spin />} />
                  <div style={{ fontSize: 9, fontWeight: 700, marginTop: 4 }}>ĐANG TẢI...</div>
                </div>
              ) : avatarUrl ? (
                <>
                  <img src={avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  <div style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'rgba(0, 0, 0, 0.45)',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    opacity: 0,
                    transition: 'opacity 0.2s',
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.opacity = '0'; }}
                  >
                    <CameraOutlined style={{ fontSize: 18 }} />
                    <span style={{ fontSize: 9, fontWeight: 700, marginTop: 2 }}>ĐỔI ẢNH</span>
                  </div>
                </>
              ) : (
                <div style={{ textAlign: 'center', color: '#00F2FE' }}>
                  <UploadOutlined style={{ fontSize: 22 }} />
                  <div style={{ fontSize: 9, fontWeight: 700, marginTop: 2 }}>TẢI ẢNH</div>
                </div>
              )}
            </div>
          </Tooltip>

          <Button 
            type="link" 
            size="small"
            icon={uploading ? <LoadingOutlined /> : <UploadOutlined />}
            loading={uploading}
            onClick={() => fileInputRef.current?.click()}
            style={{ color: '#00F2FE', fontSize: 12, marginTop: 6, fontWeight: 600 }}
          >
            {uploading ? 'Đang tải ảnh...' : 'Tải ảnh từ máy'}
          </Button>

          {/* Dãy icon mẫu nhỏ gọn để click nhanh */}
          <div style={{ display: 'flex', gap: 8, marginTop: 8, alignItems: 'center' }}>
            {AVATAR_SUGGESTIONS.map((url, idx) => {
              const isCurrent = avatarUrl === url;
              return (
                <div
                  key={idx}
                  onClick={() => handleIconSelect(url)}
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: 8,
                    cursor: 'pointer',
                    border: isCurrent ? '2px solid #00F2FE' : '1px solid rgba(255, 255, 255, 0.1)',
                    boxShadow: isCurrent ? '0 0 8px rgba(0, 242, 254, 0.4)' : 'none',
                    overflow: 'hidden',
                    transition: 'all 0.2s',
                  }}
                >
                  <img src={url} alt="preset" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                </div>
              );
            })}
          </div>
        </div>

        {/* Mẫu thiết lập sẵn (Compact Chips - Không có chữ mô tả chìm) */}
        <div style={{ marginBottom: 18 }}>
          <Text style={{ color: '#818CF8', fontWeight: 700, fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.6px', display: 'block', marginBottom: 8 }}>
            Mẫu thiết lập nhanh
          </Text>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
            {TEMPLATE_PRESETS.map((tmpl) => {
              const isSelected = selectedTemplate === tmpl.id;
              return (
                <div
                  key={tmpl.id}
                  onClick={() => handleSelectTemplate(tmpl)}
                  style={{
                    padding: '8px 10px',
                    borderRadius: 8,
                    cursor: 'pointer',
                    background: isSelected ? 'rgba(99, 102, 241, 0.2)' : 'rgba(255, 255, 255, 0.03)',
                    border: isSelected ? '1px solid #00F2FE' : '1px solid rgba(255, 255, 255, 0.06)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    color: isSelected ? '#00F2FE' : '#94A3B8',
                    fontWeight: 600,
                    fontSize: 12,
                    transition: 'all 0.2s ease',
                  }}
                >
                  <span>{tmpl.icon}</span>
                  <span>{tmpl.name}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Form nhập tên Server */}
        <Form 
          form={form} 
          layout="vertical" 
          onFinish={handleSubmit} 
          requiredMark={false}
          initialValues={{
            name: 'Không Gian Của Tôi',
            iconUrl: AVATAR_SUGGESTIONS[0],
          }}
        >
          {/* Ẩn input iconUrl ngầm */}
          <Form.Item name="iconUrl" hidden>
            <Input />
          </Form.Item>

          <Form.Item
            name="name"
            label={<Text style={{ color: '#94A3B8', fontWeight: 700, fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.5px' }}>Tên Server (Tối đa 20 ký tự)</Text>}
            rules={[
              { required: true, message: 'Vui lòng đặt tên cho Server' },
              { max: 20, message: 'Tên Server không được vượt quá 20 ký tự' },
            ]}
          >
            <Input 
              placeholder="Ví dụ: Nhóm Học SBA301" 
              size="large"
              maxLength={20}
              showCount
              style={{ height: 44, fontSize: 14 }}
            />
          </Form.Item>
        </Form>
      </div>

      {/* Modal Actions */}
      <div style={{ 
        padding: '14px 28px', 
        background: 'var(--color-bg-sidebar)', 
        borderTop: '1px solid var(--border-subtle)',
        display: 'flex', 
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        <Button 
          type="text" 
          onClick={handleClose} 
          style={{ color: '#94A3B8', fontWeight: 600 }}
        >
          Hủy bỏ
        </Button>
        <Button 
          type="primary" 
          onClick={() => form.submit()} 
          loading={loading}
          style={{ 
            height: 40,
            padding: '0 24px', 
            fontWeight: 700,
            background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
            border: 'none',
            boxShadow: '0 4px 16px rgba(0, 242, 254, 0.3)',
          }}
        >
          Khởi Tạo Không Gian
        </Button>
      </div>
    </Modal>
  );
}
