import { useEffect, useRef, useState } from 'react';
import { Alert, App, Avatar, Button, Form, Input, Modal, Spin } from 'antd';
import { CameraOutlined, CheckOutlined, SettingOutlined, UploadOutlined } from '@ant-design/icons';
import serverApi from '../../api/serverApi';
import fileApi from '../../api/fileApi';
import './server-settings.css';

export default function ServerSettingsModal({ open, server, onClose, onSaved }) {
  const { modal, message } = App.useApp();
  const [form] = Form.useForm();
  const [name, setName] = useState('');
  const [iconUrl, setIconUrl] = useState(null);
  const [baseline, setBaseline] = useState({ name: '', iconUrl: null });
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const fileInput = useRef(null);
  const session = useRef(0);
  const uploadRequest = useRef(0);
  const uploadPending = useRef(false);
  const savePending = useRef(false);
  const confirmation = useRef(null);
  const dirty = name.trim() !== baseline.name || (iconUrl || null) !== baseline.iconUrl;

  useEffect(() => {
    session.current += 1;
    uploadRequest.current += 1;
    uploadPending.current = false;
    savePending.current = false;
    setUploading(false);
    setSaving(false);
    setError(null);
    if (open && server) {
      const initial = { name: server.name, iconUrl: server.iconUrl || null };
      setBaseline(initial);
      setName(initial.name);
      setIconUrl(initial.iconUrl);
      form.resetFields();
      form.setFieldsValue({ name: initial.name });
    }
    return () => {
      session.current += 1;
      uploadRequest.current += 1;
      confirmation.current?.destroy();
      confirmation.current = null;
    };
    // A draft belongs to one opening of one server, not to background prop updates.
  }, [open, server?.id, form]);

  const resetDraft = () => {
    uploadRequest.current += 1;
    uploadPending.current = false;
    setUploading(false);
    setName(baseline.name);
    setIconUrl(baseline.iconUrl);
    form.resetFields();
    form.setFieldsValue({ name: baseline.name });
    setError(null);
  };

  const requestClose = () => {
    if (savePending.current || confirmation.current) return;
    if (!dirty) return onClose();
    const currentSession = session.current;
    confirmation.current = modal.confirm({
      title: 'Bỏ các thay đổi chưa lưu?',
      content: 'Tên và ảnh Server sẽ giữ nguyên như trước khi chỉnh sửa.',
      okText: 'Bỏ thay đổi', cancelText: 'Tiếp tục chỉnh sửa',
      onOk: () => { if (currentSession === session.current) onClose(); },
      afterClose: () => { confirmation.current = null; },
    });
  };

  const upload = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || savePending.current) return;
    const extension = file.name.split('.').at(-1)?.toLowerCase();
    if (!file.type.startsWith('image/') || !['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(extension)) {
      setError('Chọn ảnh JPG, PNG, WebP hoặc GIF.'); return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('Kích thước ảnh tối đa 5 MB.'); return;
    }
    const currentSession = session.current;
    const request = ++uploadRequest.current;
    uploadPending.current = true;
    setUploading(true);
    setError(null);
    try {
      const response = await fileApi.upload(file, 'servers');
      if (session.current !== currentSession || uploadRequest.current !== request) return;
      const url = response.data?.data?.url || response.data?.url;
      if (typeof url !== 'string' || !url || url.length > 255) throw new Error('Invalid image URL');
      setIconUrl(url);
    } catch (failure) {
      if (session.current === currentSession && uploadRequest.current === request) {
        setError(failure.response?.data?.detail || 'Không tải được ảnh. Vui lòng thử lại.');
      }
    } finally {
      if (session.current === currentSession && uploadRequest.current === request) {
        uploadPending.current = false;
        setUploading(false);
      }
    }
  };

  const save = async ({ name: draftName }) => {
    if (!dirty || savePending.current || uploadPending.current || !server?.id) return;
    const currentSession = session.current;
    savePending.current = true;
    setSaving(true);
    setError(null);
    try {
      const response = await serverApi.update(server.id, { name: draftName.trim(), iconUrl: iconUrl || null });
      if (session.current !== currentSession) return;
      const updated = response.data?.data;
      if (!updated || String(updated.id) !== String(server.id) || typeof updated.name !== 'string') {
        throw new Error('Invalid server update response');
      }
      const saved = { name: updated.name, iconUrl: updated.iconUrl || null };
      setBaseline(saved);
      setName(saved.name);
      setIconUrl(saved.iconUrl);
      form.setFieldsValue({ name: saved.name });
      onSaved(updated);
      message.success('Đã lưu thay đổi Server.');
    } catch (failure) {
      if (session.current !== currentSession) return;
      const data = failure.response?.data;
      const status = failure.response?.status;
      setError(status === 403 ? 'Bạn không còn quyền chỉnh sửa Server này.'
        : status === 404 ? 'Server không còn tồn tại.'
          : data?.fieldErrors?.[0]?.message || data?.detail || 'Không lưu được thay đổi. Vui lòng thử lại.');
    } finally {
      if (session.current === currentSession) {
        savePending.current = false;
        setSaving(false);
      }
    }
  };

  return (
    <Modal open={open} title="Cài đặt Server" onCancel={requestClose} footer={null}
      width={920} centered className="server-settings-modal" destroyOnHidden
      closable={!saving} maskClosable={!saving} keyboard={!saving}>
      <div className="server-settings">
        <aside className="server-settings__sidebar" aria-label="Các mục cài đặt Server">
          <Avatar shape="square" src={baseline.iconUrl} size={44}>{baseline.name?.[0]?.toUpperCase()}</Avatar>
          <strong title={baseline.name}>{baseline.name}</strong>
          <span>Quản lý không gian của bạn</span>
          <div className="server-settings__nav" aria-current="page"><SettingOutlined /> Tổng quan</div>
          <p>Chỉ chủ sở hữu có thể thay đổi thông tin Server.</p>
        </aside>
        <section className="server-settings__content" aria-label="Tổng quan Server">
          <div className="server-settings__intro"><h2>Tổng quan Server</h2><p>Một cái tên quen thuộc, một dấu ấn riêng cho nhóm của bạn.</p></div>
          {error && <Alert className="server-settings__error" type="error" showIcon message={error} role="alert" />}
          <Form form={form} layout="vertical" onFinish={save} requiredMark={false}>
            <div className="server-settings__image-row">
              <button className="server-settings__image" type="button" onClick={() => fileInput.current?.click()}
                disabled={saving} aria-label="Đổi ảnh Server">
                <Avatar shape="square" src={iconUrl} size={96}>{name.trim()?.[0]?.toUpperCase() || 'S'}</Avatar>
                <span className="server-settings__image-overlay">{uploading ? <Spin size="small" /> : <CameraOutlined />}</span>
              </button>
              <div className="server-settings__image-actions">
                <h3>Ảnh Server</h3><p>JPG, PNG, WebP hoặc GIF · Tối đa 5 MB</p>
                <div>
                  <Button icon={<UploadOutlined />} loading={uploading} disabled={saving}
                    onClick={() => fileInput.current?.click()}>Tải ảnh lên</Button>
                  <Button type="text" disabled={saving || (!iconUrl && !uploading)} onClick={() => {
                    uploadRequest.current += 1; uploadPending.current = false;
                    setUploading(false); setIconUrl(null); setError(null);
                  }}>Gỡ ảnh</Button>
                </div>
              </div>
              <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp,image/gif"
                onChange={upload} style={{ display: 'none' }} aria-label="Chọn file ảnh Server" />
            </div>
            <Form.Item name="name" label="Tên Server" rules={[
              { required: true, whitespace: true, message: 'Vui lòng nhập tên Server.' },
              { max: 100, message: 'Tên Server tối đa 100 ký tự.' },
            ]}>
              <Input size="large" maxLength={100} showCount disabled={saving}
                onChange={event => { setName(event.target.value); setError(null); }} />
            </Form.Item>
            <div className="server-settings__preview-label">Xem trước</div>
            <div className="server-settings__preview">
              <Avatar shape="square" src={iconUrl} size={40}>{name.trim()?.[0]?.toUpperCase() || 'S'}</Avatar>
              <div><strong>{name.trim() || 'Tên Server của bạn'}</strong><span>Không gian nhóm</span></div>
              <CheckOutlined />
            </div>
            {(dirty || uploading || saving) && <div className="server-settings__savebar" role="status">
              <span>{uploading ? 'Đang tải ảnh lên…' : 'Bạn có thay đổi chưa lưu.'}</span>
              <div>
                <Button disabled={saving} onClick={resetDraft}>Hủy thay đổi</Button>
                <Button type="primary" htmlType="submit" loading={saving} disabled={!dirty || uploading}>Lưu thay đổi</Button>
              </div>
            </div>}
          </Form>
        </section>
      </div>
    </Modal>
  );
}
