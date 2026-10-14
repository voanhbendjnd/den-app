import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { message } from 'antd';
import { getAccessToken } from '../utils/token';
import friendApi from '../api/friendApi';

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';
const PAGE_SIZE = 20;

/**
 * Hook quản lý danh sách lời mời kết bạn nhận được.
 *
 * - Load lần đầu qua POST /api/friendships/friend-requests
 * - Cursor pagination để tải thêm
 * - Subscribe WebSocket /user/queue/friend-requests/received để nhận realtime
 * - Hành động: acceptRequest(senderId), removeRequest(senderId)
 *
 * @returns {{
 *   requests: FriendRequestProjection[],
 *   loading: boolean,
 *   hasMore: boolean,
 *   loadMore: function,
 *   wsConnected: boolean,
 *   newRequestIds: Set<number>,
 *   actionLoading: Object,
 *   acceptRequest: function,
 *   removeRequest: function
 * }}
 */
export function useFriendRequests() {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);
  // IDs của request vừa nhảy vào realtime (để highlight)
  const [newRequestIds, setNewRequestIds] = useState(new Set());
  // Trạng thái loading theo từng senderId: { [senderId]: 'accepting' | 'removing' }
  const [actionLoading, setActionLoading] = useState({});

  const cursorRef = useRef(null);
  const stompRef = useRef(null);
  const loadedRef = useRef(false);

  // ── Load lần đầu ───────────────────────────────────────────────────────────
  const fetchRequests = useCallback(async (cursor = null, append = false) => {
    setLoading(true);
    try {
      const res = await friendApi.getFriendRequests(PAGE_SIZE, cursor);
      // Backend trả trực tiếp ResultPaginationCursorTime (không bọc wrapper)
      const data = res.data?.data ?? res.data ?? {};
      const items = data.result ?? [];
      const nextCursor = data.nextCursor ?? null;
      const more = data.hasMore ?? false;

      cursorRef.current = nextCursor;
      setHasMore(more);

      if (append) {
        setRequests((prev) => {
          // Tránh duplicate theo senderId
          const existingIds = new Set(prev.map((r) => r.senderId));
          const unique = items.filter((r) => !existingIds.has(r.senderId));
          return [...prev, ...unique];
        });
      } else {
        setRequests(items);
      }
    } catch (err) {
      console.error('[useFriendRequests] Load failed:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Load lần đầu khi mount
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    fetchRequests(null, false);
  }, [fetchRequests]);

  // ── Load more (pagination) ──────────────────────────────────────────────────
  const loadMore = useCallback(() => {
    if (!hasMore || loading) return;
    fetchRequests(cursorRef.current, true);
  }, [hasMore, loading, fetchRequests]);

  // ── Xử lý khi có lời mời kết bạn mới đến (realtime) ───────────────────────
  const handleIncomingRequest = useCallback((action) => {
    if (!action || !action.senderId) return;
    const newReq = {
      senderId: action.senderId,
      username: action.username,
      name: action.name,
      avatar: action.avatar,
      lastModifiedDate: action.lastModifiedDate
        ? new Date(action.lastModifiedDate).toISOString()
        : new Date().toISOString(),
    };

    setRequests((prev) => {
      // Không duplicate
      if (prev.some((r) => r.senderId === newReq.senderId)) return prev;
      return [newReq, ...prev];
    });

    // Đánh dấu để highlight animation
    setNewRequestIds((prev) => new Set([...prev, action.senderId]));
    // Xóa highlight sau 4 giây
    setTimeout(() => {
      setNewRequestIds((prev) => {
        const next = new Set(prev);
        next.delete(action.senderId);
        return next;
      });
    }, 4000);
  }, []);

  // Lắng nghe event broadcast từ useGlobalFriendNotification
  useEffect(() => {
    const onFriendReq = (e) => {
      if (e.detail) {
        handleIncomingRequest(e.detail);
      }
    };
    window.addEventListener('denhub:friend-request-received', onFriendReq);
    return () => {
      window.removeEventListener('denhub:friend-request-received', onFriendReq);
    };
  }, [handleIncomingRequest]);

  // ── WebSocket STOMP ─────────────────────────────────────────────────────────
  useEffect(() => {
    const token = getAccessToken();
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        setWsConnected(true);

        // Subscribe nhận lời mời kết bạn realtime
        client.subscribe('/user/queue/friend-requests/received', (frame) => {
          try {
            const action = JSON.parse(frame.body);
            handleIncomingRequest(action);
          } catch (err) {
            console.error('[useFriendRequests] WS parse error:', err);
          }
        });
      },
      onDisconnect: () => setWsConnected(false),
      onStompError: (frame) => {
        console.error('[useFriendRequests] STOMP error:', frame);
      },
    });

    stompRef.current = client;
    client.activate();

    return () => {
      if (stompRef.current) {
        stompRef.current.deactivate();
        stompRef.current = null;
      }
      setWsConnected(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Chấp nhận lời mời kết bạn ──────────────────────────────────────────────
  const acceptRequest = useCallback(async (senderId) => {
    setActionLoading((prev) => ({ ...prev, [senderId]: 'accepting' }));
    try {
      await friendApi.acceptFriendRequest(senderId);
      // Xóa item khỏi danh sách lời mời
      setRequests((prev) => prev.filter((r) => r.senderId !== senderId));
      message.success('Đã chấp nhận lời mời kết bạn!');
    } catch (err) {
      console.error('[useFriendRequests] acceptRequest failed:', err);
      const errMsg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        'Không thể chấp nhận lời mời kết bạn. Vui lòng thử lại!';
      message.error(errMsg);
    } finally {
      setActionLoading((prev) => {
        const next = { ...prev };
        delete next[senderId];
        return next;
      });
    }
  }, []);

  // ── Từ chối / Hủy lời mời kết bạn ──────────────────────────────────────────
  const removeRequest = useCallback(async (senderId) => {
    setActionLoading((prev) => ({ ...prev, [senderId]: 'removing' }));
    try {
      await friendApi.removeFriendRequest(senderId);
      // Xóa item khỏi danh sách lời mời
      setRequests((prev) => prev.filter((r) => r.senderId !== senderId));
      message.info('Đã từ chối lời mời kết bạn.');
    } catch (err) {
      console.error('[useFriendRequests] removeRequest failed:', err);
      const errMsg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        'Không thể từ chối lời mời kết bạn. Vui lòng thử lại!';
      message.error(errMsg);
    } finally {
      setActionLoading((prev) => {
        const next = { ...prev };
        delete next[senderId];
        return next;
      });
    }
  }, []);

  return {
    requests,
    loading,
    hasMore,
    loadMore,
    wsConnected,
    newRequestIds,
    actionLoading,
    acceptRequest,
    removeRequest,
  };
}

