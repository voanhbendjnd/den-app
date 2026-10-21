import { useEffect, useRef, useState } from 'react';
import { Alert, App, Modal } from 'antd';
import serverApi from '../../api/serverApi';

// Mounted for one confirmation only; reopening always starts a fresh three-second delay.
export default function DeleteServerModal({ server, onClose, onDeleted, onBusyChange }) {
  const { message } = App.useApp();
  const deadline = useRef(performance.now() + 3000);
  const pending = useRef(false);
  const active = useRef(true);
  const [remaining, setRemaining] = useState(3);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    active.current = true;
    const timer = window.setInterval(() => {
      const seconds = Math.max(0, Math.ceil((deadline.current - performance.now()) / 1000));
      setRemaining(seconds);
      if (seconds === 0) window.clearInterval(timer);
    }, 100);
    return () => { active.current = false; window.clearInterval(timer); };
  }, []);

  const confirm = async () => {
    if (pending.current || performance.now() < deadline.current) return;
    pending.current = true;
    setDeleting(true);
    setError(null);
    onBusyChange(true);
    try {
      await serverApi.delete(server.id);
      // The dock must also be updated if the user navigated away while DELETE was pending.
      onDeleted(server.id);
      if (active.current) message.success('Đã xóa Server.');
    } catch (failure) {
      if (!active.current) return;
      const status = failure.response?.status;
      setError(status === 403 ? 'Chỉ chủ sở hữu đang có quyền mới được xóa Server này.'
        : status === 404 ? 'Server không còn tồn tại. Hãy đóng hộp thoại và tải lại danh sách.'
          : status === 409 ? 'Dữ liệu tin nhắn có liên kết với hội thoại riêng nên chưa thể xóa an toàn. Vui lòng liên hệ quản trị viên.'
            : status === 400 ? 'Server ID không hợp lệ. Vui lòng tải lại trang.'
              : 'Không thể xác nhận Server đã được xóa. Vui lòng tải lại danh sách hoặc thử lại.');
    } finally {
      pending.current = false;
      if (active.current) { setDeleting(false); onBusyChange(false); }
    }
  };

  return (
    <Modal open title="Xóa vĩnh viễn Server?" onOk={confirm}
      onCancel={() => { if (!pending.current) onClose(); }}
      okText={remaining > 0 ? `Xác nhận xóa (${remaining}s)` : 'Xác nhận xóa'}
      cancelText="Hủy" confirmLoading={deleting}
      okButtonProps={{ danger: true, disabled: remaining > 0 || deleting }}
      cancelButtonProps={{ disabled: deleting }}
      closable={!deleting} maskClosable={!deleting} keyboard={!deleting} centered>
      <p>Bạn sắp xóa Server <strong>{server.name}</strong>.</p>
      <Alert type="warning" showIcon message="Thao tác này không thể hoàn tác."
        description="Toàn bộ kênh, bao gồm #general, tin nhắn trong kênh, tư cách thành viên và mã mời của Server sẽ bị xóa vĩnh viễn. Mọi thành viên sẽ mất quyền truy cập Server." />
      <p role="status" aria-live="polite">{remaining > 0
        ? `Vui lòng đọc cảnh báo. Bạn có thể xác nhận sau ${remaining} giây.`
        : 'Hãy chắc chắn trước khi xác nhận xóa.'}</p>
      {error && <Alert type="error" showIcon message={error} role="alert" />}
    </Modal>
  );
}
