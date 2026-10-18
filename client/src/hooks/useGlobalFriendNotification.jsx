import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import { notification, Button } from 'antd';
import { UserAddOutlined } from '@ant-design/icons';
import { getAccessToken } from '../utils/token';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

/**
 * Global hook to listen for real-time friend request events across the application.
 * Mounted in MainLayout so notifications pop up even if user is on other pages.
 */
export function useGlobalFriendNotification() {
  const navigate = useNavigate();
  const stompRef = useRef(null);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        // 1. Nhận thông báo có lời mời kết bạn mới
        client.subscribe('/user/queue/friend-requests/received', (frame) => {
          try {
            const action = JSON.parse(frame.body);
            // Broadcast event nội bộ để các hook khác (useFriendRequests) cập nhật state
            window.dispatchEvent(
              new CustomEvent('denhub:friend-request-received', { detail: action })
            );

            const senderName = action.name || action.username || 'Một người dùng';
            notification.open({
              key: `friend-req-${action.senderId}`,
              message: 'Lời mời kết bạn mới',
              description: `${senderName} vừa gửi lời mời kết bạn tới bạn!`,
              icon: <UserAddOutlined style={{ color: '#6366F1' }} />,
              btn: (
                <Button
                  type="primary"
                  size="small"
                  style={{
                    background: 'linear-gradient(135deg, #6366F1, #00F2FE)',
                    border: 'none',
                    fontWeight: 600,
                  }}
                  onClick={() => {
                    notification.destroy(`friend-req-${action.senderId}`);
                    navigate('/friends');
                  }}
                >
                  Xem lời mời
                </Button>
              ),
              placement: 'bottomRight',
              duration: 6,
            });
          } catch (err) {
            console.error('[GlobalFriendNotification] Parse error:', err);
          }
        });

        // 2. Nhận thông báo lời mời kết bạn được chấp nhận (cập nhật state nội bộ, không hiện popup thông báo để tránh spam)
        client.subscribe('/user/queue/friend-requests/accepted', (frame) => {
          try {
            const action = JSON.parse(frame.body);
            window.dispatchEvent(
              new CustomEvent('denhub:friend-request-accepted', { detail: action })
            );
          } catch (err) {
            console.error('[GlobalFriendNotification] Parse accepted error:', err);
          }
        });
      },
      onStompError: (frame) => {
        console.error('[GlobalFriendNotification] STOMP error:', frame);
      },
    });

    stompRef.current = client;
    client.activate();

    return () => {
      if (stompRef.current) {
        stompRef.current.deactivate();
        stompRef.current = null;
      }
    };
  }, [navigate]);
}
