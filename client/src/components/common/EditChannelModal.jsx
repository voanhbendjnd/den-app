import { useState, useEffect } from 'react';
import { Modal, Form, Input, InputNumber, Button, Typography, message, Space, Alert, Tooltip } from 'antd';
import { 
  NumberOutlined, 
  EditOutlined,
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined
} from '@ant-design/icons';
import channelApi from '../../api/channelApi';
import { formatChannelName, getChannelEditErrorMessage } from '../../utils/channelUtils';

const { Title, Text } = Typography;

export { formatChannelName, getChannelEditErrorMessage };

export default function EditChannelModal({ 
  open, 
  onClose, 
  onSuccess, 
  channel, 
  server, 
  onDelete, 
  totalChannelsCount 
}) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);

  const isGeneral = channel?.name?.toLowerCase() === 'general';
  const isOnlyChannel = typeof totalChannelsCount === 'number' && totalChannelsCount <= 1;
  const cannotDelete = isGeneral || isOnlyChannel;

  useEffect(() => {
    if (open && channel) {
      setErrorMsg(null);
      form.setFieldsValue({
        name: channel.name || '',
        position: channel.position ?? 0,
      });
    }
  }, [open, channel, form]);

  const handleNameChange = (e) => {
    const formatted = formatChannelName(e.target.value);
    form.setFieldsValue({ name: formatted });
  };

  const handleSubmit = async (values) => {
    if (!channel?.id) {
      message.error('Không tìm thấy thông tin Channel hợp lệ!');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      const payload = {
        name: values.name.trim().toLowerCase(),
        position: values.position != null ? Number(values.position) : undefined,
      };

      const res = await channelApi.update(channel.id, payload);
      const updated = res.data?.data || res.data;

      message.success(`Đã cập nhật kênh #${updated?.name || payload.name} thành công!`);
      onSuccess?.(updated);
      onClose();
    } catch (err) {
      setErrorMsg(getChannelEditErrorMessage(err, values.name));
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteClick = () => {
    if (cannotDelete) return;
    onClose();
    onDelete?.(channel);
  };

  const deleteTooltip = isGeneral
    ? 'Kênh #general là kênh mặc định, không thể xóa'
    : isOnlyChannel
    ? 'Máy chủ phải có ít nhất 1 kênh'
    : 'Xóa vĩnh viễn kênh này';

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      centered
      width={460}
      destroyOnClose
      maskClosable={!loading}
      styles={{
        content: {
          background: 'linear-gradient(145deg, #161828 0%, #0d0e18 100%)',
          border: '1px solid rgba(0, 242, 254, 0.25)',
          borderRadius: 16,
          boxShadow: '0 16px 40px rgba(0, 0, 0, 0.6), 0 0 20px rgba(0, 242, 254, 0.1)',
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
            background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.25) 0%, rgba(0, 242, 254, 0.2) 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 24,
            color: '#00f2fe',
            border: '1px solid rgba(0, 242, 254, 0.3)',
            boxShadow: '0 0 15px rgba(0, 242, 254, 0.2)',
          }}
        >
          <EditOutlined />
        </div>
        <Title level={4} style={{ color: '#fff', margin: 0, fontWeight: 700 }}>
          Cài Đặt Kênh
        </Title>
        <Text style={{ color: '#94a3b8', fontSize: 13, display: 'block', marginTop: 4 }}>
          {server?.name ? `Chỉnh sửa kênh trong "${server.name}"` : 'Cập nhật thông tin kênh trò chuyện'}
        </Text>
      </div>

      {errorMsg && (
        <Alert
          message={errorMsg}
          type="error"
          showIcon
          style={{
            marginBottom: 16,
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: 8,
            color: '#fca5a5',
          }}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        requiredMark={false}
      >
        <Form.Item
          label={
            <span style={{ color: '#cbd5e1', fontWeight: 600, fontSize: 13 }}>
              TÊN KÊNH CHAT
            </span>
          }
          name="name"
          rules={[
            { required: true, message: 'Vui lòng nhập tên kênh!' },
            { min: 2, message: 'Tên kênh phải có ít nhất 2 ký tự!' },
            { max: 20, message: 'Tên kênh không được vượt quá 20 ký tự!' },
            {
              pattern: /^[a-z0-9-_]+$/,
              message: 'Tên kênh chỉ được chứa chữ thường không dấu, số và dấu gạch ngang/dưới!',
            },
          ]}
        >
          <Input
            prefix={<NumberOutlined style={{ color: '#00f2fe', marginRight: 4 }} />}
            placeholder="vd: dev-chat, general"
            onChange={handleNameChange}
            maxLength={20}
            size="large"
            disabled={loading}
            style={{
              background: 'rgba(15, 23, 42, 0.65)',
              borderColor: 'rgba(148, 163, 184, 0.25)',
              color: '#f8fafc',
              borderRadius: 8,
              fontFamily: 'monospace',
              fontSize: 14,
            }}
          />
        </Form.Item>

        <Form.Item
          label={
            <span style={{ color: '#cbd5e1', fontWeight: 600, fontSize: 13 }}>
              THỨ TỰ HIỂN THỊ (VỊ TRÍ)
            </span>
          }
          name="position"
          rules={[
            { type: 'number', min: 0, message: 'Thứ tự vị trí phải là số không âm (>= 0)!' },
          ]}
        >
          <InputNumber
            min={0}
            max={999}
            size="large"
            disabled={loading}
            style={{
              width: '100%',
              background: 'rgba(15, 23, 42, 0.65)',
              borderColor: 'rgba(148, 163, 184, 0.25)',
              color: '#f8fafc',
              borderRadius: 8,
            }}
          />
        </Form.Item>

        <div
          style={{
            marginTop: 24,
            display: 'flex',
            justifyContent: onDelete ? 'space-between' : 'flex-end',
            alignItems: 'center',
            gap: 12,
          }}
        >
          {onDelete && (
            <Tooltip title={deleteTooltip}>
              <span>
                <Button
                  danger
                  type="text"
                  icon={<DeleteOutlined />}
                  disabled={loading || cannotDelete}
                  onClick={handleDeleteClick}
                  style={{
                    color: cannotDelete ? '#64748b' : '#f87171',
                    borderRadius: 8,
                    padding: '4px 12px',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    background: cannotDelete ? 'rgba(255, 255, 255, 0.03)' : 'rgba(239, 68, 68, 0.1)',
                    border: `1px solid ${cannotDelete ? 'rgba(255, 255, 255, 0.08)' : 'rgba(239, 68, 68, 0.3)'}`,
                    cursor: cannotDelete ? 'not-allowed' : 'pointer',
                  }}
                >
                  Xóa kênh
                </Button>
              </span>
            </Tooltip>
          )}

          <Space orientation="horizontal" style={{ justifyContent: 'flex-end' }}>
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
              htmlType="submit"
              loading={loading}
              icon={<CheckOutlined />}
              style={{
                borderRadius: 8,
                background: 'linear-gradient(135deg, #6366f1 0%, #00f2fe 100%)',
                borderColor: 'transparent',
                fontWeight: 600,
                boxShadow: '0 4px 14px rgba(0, 242, 254, 0.3)',
              }}
            >
              Lưu thay đổi
            </Button>
          </Space>
        </div>
      </Form>
    </Modal>
  );
}
