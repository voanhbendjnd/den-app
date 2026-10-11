import { useState, useEffect } from 'react';
import { Modal, Form, Input, Button, Typography, message, Tag, Space, Alert } from 'antd';
import { 
  NumberOutlined, 
  PlusOutlined, 
  FireOutlined, 
  MessageOutlined, 
  NotificationOutlined, 
  QuestionCircleOutlined,
  FolderOpenOutlined 
} from '@ant-design/icons';
import channelApi from '../../api/channelApi';

const { Title, Text } = Typography;

// Gợi ý kênh mẫu thông dụng
const QUICK_SUGGESTIONS = [
  { name: 'general', label: 'general', icon: <MessageOutlined /> },
  { name: 'dev-chat', label: 'dev-chat', icon: <FireOutlined /> },
  { name: 'thong-bao', label: 'thong-bao', icon: <NotificationOutlined /> },
  { name: 'hoi-dap', label: 'hoi-dap', icon: <QuestionCircleOutlined /> },
  { name: 'tai-lieu', label: 'tai-lieu', icon: <FolderOpenOutlined /> },
];

export default function CreateChannelModal({ open, onClose, onSuccess, server }) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);

  useEffect(() => {
    if (open) {
      form.resetFields();
      setErrorMsg(null);
      // Gợi ý mặc định
      form.setFieldsValue({ name: 'general' });
    }
  }, [open, form]);

  // Chuẩn hóa tên kênh tự động (chữ thường, thay khoảng trắng bằng dấu gạch ngang)
  const handleNameChange = (e) => {
    const raw = e.target.value;
    const formatted = raw
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-_]/g, '');
    form.setFieldsValue({ name: formatted });
  };

  const handleSelectQuickSuggestion = (name) => {
    form.setFieldsValue({ name });
    setErrorMsg(null);
  };

  const handleSubmit = async (values) => {
    if (!server?.id) {
      message.error('Không tìm thấy thông tin Server hợp lệ!');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      const payload = {
        serverId: server.id,
        name: values.name.trim().toLowerCase(),
      };

      const res = await channelApi.create(payload);
      const newChannel = res.data?.data || res.data;

      message.success(`Đã tạo kênh #${newChannel?.name || payload.name} thành công!`);
      form.resetFields();
      onSuccess?.(newChannel);
      onClose();
    } catch (err) {
      const errorData = err.response?.data;
      if (errorData?.errorKey === 'namealreadyexists' || errorData?.message?.includes('already exists') || errorData?.title?.includes('already exists')) {
        setErrorMsg(`Tên kênh "#${values.name}" đã tồn tại trong server này. Vui lòng chọn tên khác!`);
      } else if (errorData?.errorKey === 'forbidden') {
        setErrorMsg('Bạn không có quyền tạo kênh trong server này!');
      } else if (errorData?.violations?.length > 0) {
        setErrorMsg(errorData.violations[0].message);
      } else if (errorData?.message) {
        setErrorMsg(errorData.message);
      } else {
        setErrorMsg('Không thể tạo kênh. Vui lòng thử lại sau!');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      centered
      width={460}
      destroyOnClose
      styles={{
        content: {
          background: '#151D2E',
          borderRadius: 20,
          border: '1px solid rgba(99, 102, 241, 0.25)',
          boxShadow: '0 24px 60px rgba(0, 0, 0, 0.8), 0 0 30px rgba(99, 102, 241, 0.15)',
          padding: 28,
        },
      }}
    >
      {/* Header */}
      <div style={{ textAlign: 'center', marginBottom: 22 }}>
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
            margin: '0 auto 12px',
            color: '#00F2FE',
            fontSize: 24,
            boxShadow: '0 8px 20px rgba(0, 242, 254, 0.2)',
          }}
        >
          <NumberOutlined />
        </div>
        <Title level={4} style={{ color: '#F1F5F9', margin: 0, fontWeight: 800 }}>
          Tạo Kênh Mới
        </Title>
        <Text style={{ color: '#94A3B8', fontSize: 13, marginTop: 4, display: 'block' }}>
          trong không gian <span style={{ color: '#00F2FE', fontWeight: 600 }}>{server?.name || 'Server'}</span>
        </Text>
      </div>

      {errorMsg && (
        <Alert
          message={errorMsg}
          type="error"
          showIcon
          style={{
            marginBottom: 18,
            borderRadius: 10,
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.25)',
          }}
          closable
          onClose={() => setErrorMsg(null)}
        />
      )}

      {/* Form */}
      <Form form={form} layout="vertical" onFinish={handleSubmit} requiredMark={false}>
        {/* Tên Kênh */}
        <Form.Item
          name="name"
          label={
            <span style={{ color: '#E2E8F0', fontWeight: 600, fontSize: 13 }}>
              TÊN KÊNH CHAT
            </span>
          }
          rules={[
            { required: true, message: 'Vui lòng nhập tên kênh!' },
            { min: 2, message: 'Tên kênh phải có ít nhất 2 ký tự!' },
            { max: 20, message: 'Tên kênh tối đa 20 ký tự!' },
          ]}
        >
          <Input
            prefix={<NumberOutlined style={{ color: '#64748B', fontSize: 15 }} />}
            placeholder="ví dụ: general, dev-chat, thong-bao"
            onChange={handleNameChange}
            size="large"
            style={{
              background: '#0D131F',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: 10,
              color: '#F1F5F9',
              fontWeight: 500,
            }}
          />
        </Form.Item>

        {/* Gợi ý nhanh (Quick Suggestions) */}
        <div style={{ marginBottom: 24 }}>
          <Text style={{ color: '#64748B', fontSize: 12, display: 'block', marginBottom: 8 }}>
            Gợi ý nhanh (bấm để chọn):
          </Text>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {QUICK_SUGGESTIONS.map((item) => (
              <Tag
                key={item.name}
                onClick={() => handleSelectQuickSuggestion(item.name)}
                style={{
                  cursor: 'pointer',
                  borderRadius: 6,
                  padding: '4px 10px',
                  background: 'rgba(99, 102, 241, 0.1)',
                  border: '1px solid rgba(99, 102, 241, 0.25)',
                  color: '#CBD5E1',
                  fontSize: 12,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  transition: 'all 0.2s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = '#00F2FE';
                  e.currentTarget.style.color = '#00F2FE';
                  e.currentTarget.style.background = 'rgba(0, 242, 254, 0.15)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(99, 102, 241, 0.25)';
                  e.currentTarget.style.color = '#CBD5E1';
                  e.currentTarget.style.background = 'rgba(99, 102, 241, 0.1)';
                }}
              >
                {item.icon} #{item.label}
              </Tag>
            ))}
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end', marginTop: 8 }}>
          <Button
            onClick={onClose}
            disabled={loading}
            style={{
              height: 42,
              borderRadius: 10,
              background: 'transparent',
              borderColor: 'rgba(255, 255, 255, 0.1)',
              color: '#94A3B8',
              fontWeight: 600,
              padding: '0 20px',
            }}
          >
            Hủy
          </Button>

          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            icon={<PlusOutlined />}
            style={{
              height: 42,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
              border: 'none',
              fontWeight: 700,
              padding: '0 24px',
              boxShadow: '0 4px 16px rgba(0, 242, 254, 0.3)',
            }}
          >
            Tạo Kênh
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
