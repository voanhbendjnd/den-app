import { useState, useEffect, useCallback } from 'react';
import {
  Avatar,
  Typography,
  Button,
  Tag,
  Skeleton,
  Tooltip,
  message,
} from 'antd';
import {
  UserOutlined,
  MailOutlined,
  CalendarOutlined,
  SafetyCertificateOutlined,
  IdcardOutlined,
  ReloadOutlined,
  EditOutlined,
  KeyOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  WarningOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../hooks/useAuth';
import userApi from '../../api/userApi';
import EditProfileModal from '../../components/common/EditProfileModal';
import {
  extractProfileData,
  formatJoinDate,
  getProfileErrorMessage,
  getRoleDisplays,
} from '../../utils/profileUtils';
import './profile.css';

const { Title, Text } = Typography;

export default function ProfilePage() {
  const { currentUser, updateCurrentUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  // Gọi API lấy thông tin hồ sơ người dùng
  const fetchProfile = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      const res = await userApi.getProfile();
      const extracted = extractProfileData(res);

      if (!extracted) {
        throw new Error('Dữ liệu hồ sơ trả về từ máy chủ không hợp lệ.');
      }

      setProfile(extracted);

      // Đồng bộ thông tin mới nhất vào AuthContext / localStorage nếu cần
      if (typeof updateCurrentUser === 'function') {
        updateCurrentUser({
          id: extracted.id,
          email: extracted.email,
          name: extracted.name,
          displayName: extracted.name,
          username: extracted.username,
          avatar: extracted.avatar,
          authorities: extracted.authorities,
        });
      }

      if (isManualRefresh) {
        message.success('Đã cập nhật thông tin hồ sơ mới nhất!');
      }
    } catch (err) {
      const errMsg = getProfileErrorMessage(err);
      setError(errMsg);
      if (isManualRefresh) {
        message.error(errMsg);
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [updateCurrentUser]);

  useEffect(() => {
    fetchProfile(false);
  }, [fetchProfile]);

  const handleEditSuccess = (updatedData) => {
    setProfile((prev) => ({
      ...(prev || {}),
      ...updatedData,
    }));
  };

  // Dữ liệu hiển thị fallback kết hợp giữa API data và currentUser
  const displayData = profile || {
    id: currentUser?.id || '—',
    name: currentUser?.name || currentUser?.displayName || 'Người dùng',
    username: currentUser?.username || currentUser?.email?.split('@')[0] || 'user',
    email: currentUser?.email || '—',
    avatar: currentUser?.avatar || null,
    activated: true,
    createdDate: null,
    authorities: currentUser?.authorities || ['ROLE_USER'],
  };

  const roleList = getRoleDisplays(displayData.authorities);
  const initialLetter = displayData.name ? displayData.name.trim().charAt(0).toUpperCase() : 'U';

  return (
    <div className="profile-page-wrapper">
      <div className="profile-container">
        {/* Error State */}
        {error && !loading && !profile && (
          <div className="profile-error-card">
            <WarningOutlined style={{ fontSize: 36, color: '#EF4444', marginBottom: 12 }} />
            <div className="profile-error-title">Không thể tải thông tin hồ sơ</div>
            <div className="profile-error-desc">{error}</div>
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={() => fetchProfile(false)}
              style={{
                background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                border: 'none',
                fontWeight: 600,
              }}
            >
              Thử lại ngay
            </Button>
          </div>
        )}

        {/* Loading State Skeleton */}
        {loading && (
          <div className="profile-main-card">
            <div className="profile-cover-banner" />
            <div className="profile-body-content" style={{ paddingTop: 60 }}>
              <Skeleton active avatar={{ size: 96, shape: 'circle' }} paragraph={{ rows: 6 }} />
            </div>
          </div>
        )}

        {/* Loaded Profile View */}
        {(!loading || profile) && (!error || profile) && (
          <div className="profile-main-card">
            {/* Hero Cover Banner */}
            <div className="profile-cover-banner">
              <div className="profile-cover-badge">
                <SafetyCertificateOutlined />
                <span>DENHUB PROFILE</span>
              </div>
            </div>

            {/* Profile Body */}
            <div className="profile-body-content">
              {/* Avatar + Actions Row */}
              <div className="profile-avatar-row">
                <div className="profile-avatar-wrapper">
                  <Avatar
                    size={96}
                    src={displayData.avatar}
                    icon={!displayData.avatar ? <UserOutlined /> : undefined}
                    className="profile-avatar"
                  >
                    {!displayData.avatar && initialLetter}
                  </Avatar>
                  <Tooltip title={displayData.activated ? 'Tài khoản hoạt động' : 'Tài khoản tạm khóa'}>
                    <div
                      className="profile-avatar-status"
                      style={{
                        background: displayData.activated ? '#10B981' : '#EF4444',
                        boxShadow: displayData.activated ? '0 0 10px #10B981' : '0 0 10px #EF4444',
                      }}
                    />
                  </Tooltip>
                </div>

                <div className="profile-actions-toolbar">
                  <Button
                    type="primary"
                    icon={<EditOutlined />}
                    onClick={() => setIsEditModalOpen(true)}
                    style={{
                      background: 'linear-gradient(135deg, #6366F1 0%, #00F2FE 100%)',
                      border: 'none',
                      fontWeight: 600,
                      boxShadow: '0 4px 14px rgba(99, 102, 241, 0.35)',
                    }}
                  >
                    Chỉnh sửa hồ sơ
                  </Button>
                  <Tooltip title="Tải lại thông tin từ hệ thống">
                    <Button
                      icon={<ReloadOutlined spin={refreshing} />}
                      onClick={() => fetchProfile(true)}
                      loading={refreshing}
                      style={{
                        background: 'rgba(255, 255, 255, 0.05)',
                        borderColor: 'var(--border-subtle)',
                        color: '#94A3B8',
                      }}
                    >
                      Làm mới
                    </Button>
                  </Tooltip>
                </div>
              </div>

              {/* Title & Identifiers */}
              <div className="profile-title-section">
                <h1 className="profile-display-name">{displayData.name}</h1>
                <div className="profile-username-tag">@{displayData.username}</div>

                <div className="profile-badges-row">
                  {roleList.map((role) => (
                    <Tag
                      key={role.authority}
                      color={role.tagColor}
                      className="profile-role-tag"
                      style={{
                        borderColor: role.color,
                        boxShadow: `0 0 8px ${role.color}40`,
                      }}
                    >
                      {role.label}
                    </Tag>
                  ))}

                  <span
                    className={`profile-status-badge ${displayData.activated ? 'active' : 'inactive'}`}
                  >
                    {displayData.activated ? (
                      <>
                        <CheckCircleFilled /> Đã kích hoạt
                      </>
                    ) : (
                      <>
                        <CloseCircleFilled /> Chưa kích hoạt
                      </>
                    )}
                  </span>
                </div>
              </div>

              {/* Detailed Info Grid */}
              <div className="profile-details-grid">
                {/* Email Card */}
                <div className="profile-info-card">
                  <div className="profile-info-icon-box">
                    <MailOutlined />
                  </div>
                  <div className="profile-info-meta">
                    <div className="profile-info-label">Địa chỉ Email</div>
                    <div className="profile-info-value">{displayData.email}</div>
                  </div>
                </div>

                {/* Account ID Card */}
                <div className="profile-info-card">
                  <div className="profile-info-icon-box">
                    <IdcardOutlined />
                  </div>
                  <div className="profile-info-meta">
                    <div className="profile-info-label">Mã định danh (ID)</div>
                    <div className="profile-info-value" style={{ fontFamily: 'var(--font-mono, monospace)' }}>
                      #{String(displayData.id).padStart(4, '0')}
                    </div>
                  </div>
                </div>

                {/* Join Date Card */}
                <div className="profile-info-card">
                  <div className="profile-info-icon-box">
                    <CalendarOutlined />
                  </div>
                  <div className="profile-info-meta">
                    <div className="profile-info-label">Ngày gia nhập</div>
                    <div className="profile-info-value">
                      {formatJoinDate(displayData.createdDate, true)}
                    </div>
                  </div>
                </div>

                {/* System Permissions Card */}
                <div className="profile-info-card">
                  <div className="profile-info-icon-box">
                    <SafetyCertificateOutlined />
                  </div>
                  <div className="profile-info-meta">
                    <div className="profile-info-label">Quyền hạn hệ thống</div>
                    <div className="profile-info-value">
                      {displayData.authorities.join(', ')}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Quick Actions Footer */}
            <div className="profile-footer-box">
              <div style={{ color: '#64748B', fontSize: 13 }}>
                Hồ sơ thành viên chính thức trên nền tảng DenHub.
              </div>
              <div style={{ display: 'flex', gap: 10 }}>
                <Button
                  icon={<EditOutlined />}
                  onClick={() => setIsEditModalOpen(true)}
                  style={{
                    background: 'rgba(255, 255, 255, 0.05)',
                    borderColor: 'var(--border-subtle)',
                    color: '#F1F5F9',
                    fontWeight: 600,
                  }}
                >
                  Chỉnh sửa hồ sơ
                </Button>
                <Button
                  icon={<KeyOutlined />}
                  onClick={() => message.info('Chức năng Đổi mật khẩu sẽ sẵn sàng ở giai đoạn tiếp theo!')}
                  style={{
                    background: 'rgba(99, 102, 241, 0.12)',
                    borderColor: 'rgba(99, 102, 241, 0.4)',
                    color: '#818CF8',
                    fontWeight: 600,
                  }}
                >
                  Đổi mật khẩu
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* Modal Chỉnh Sửa Hồ Sơ */}
        <EditProfileModal
          open={isEditModalOpen}
          onClose={() => setIsEditModalOpen(false)}
          onSuccess={handleEditSuccess}
          currentProfile={displayData}
        />
      </div>
    </div>
  );
}
