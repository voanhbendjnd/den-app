import { useEffect, useRef, useState, useCallback } from 'react';
import { message } from 'antd';
import friendApi from '../api/friendApi';

const PAGE_SIZE = 20;

/**
 * Hook quản lý danh sách người dùng đã bị chặn (status = BLOCKED, actionUserId = currentUserId).
 *
 * - Tải ban đầu qua POST /api/friendships/blocked-users
 * - Cursor pagination (loadMore)
 * - Hành động: unblockUser(friendId)
 */
export function useBlockedUsers() {
  const [blockedUsers, setBlockedUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [unblockLoading, setUnblockLoading] = useState({});

  const cursorRef = useRef(null);
  const loadedRef = useRef(false);

  // ── Fetch Blocked Users ───────────────────────────────────────────────────
  const fetchBlockedUsers = useCallback(async (cursor = null, append = false) => {
    setLoading(true);
    try {
      const res = await friendApi.getBlockedUsers(PAGE_SIZE, cursor);
      const data = res.data?.data ?? res.data ?? {};
      const items = data.result ?? [];
      const nextCursor = data.nextCursor ?? null;
      const more = data.hasMore ?? false;

      cursorRef.current = nextCursor;
      setHasMore(more);

      if (append) {
        setBlockedUsers((prev) => {
          const existingIds = new Set(prev.map((f) => f.senderId));
          const unique = items.filter((f) => !existingIds.has(f.senderId));
          return [...prev, ...unique];
        });
      } else {
        setBlockedUsers(items);
      }
    } catch (err) {
      console.error('[useBlockedUsers] Load blocked users failed:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải lần đầu khi mount
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    fetchBlockedUsers(null, false);
  }, [fetchBlockedUsers]);

  // Load more (cursor pagination)
  const loadMore = useCallback(() => {
    if (!hasMore || loading) return;
    fetchBlockedUsers(cursorRef.current, true);
  }, [hasMore, loading, fetchBlockedUsers]);

  // Refresh
  const refresh = useCallback(() => {
    cursorRef.current = null;
    fetchBlockedUsers(null, false);
  }, [fetchBlockedUsers]);

  // ── Gỡ chặn người dùng (unblock user) ─────────────────────────────────────
  const unblockUser = useCallback(async (friendId) => {
    setUnblockLoading((prev) => ({ ...prev, [friendId]: true }));
    try {
      await friendApi.unblockUser(friendId);
      // Xóa người này khỏi danh sách đã chặn
      setBlockedUsers((prev) => prev.filter((f) => f.senderId !== friendId));
      message.success('Đã bỏ chặn người dùng thành công.');
    } catch (err) {
      console.error('[useBlockedUsers] unblockUser failed:', err);
      const errMsg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        'Không thể bỏ chặn người dùng. Vui lòng thử lại!';
      message.error(errMsg);
    } finally {
      setUnblockLoading((prev) => {
        const next = { ...prev };
        delete next[friendId];
        return next;
      });
    }
  }, []);

  return {
    blockedUsers,
    loading,
    hasMore,
    loadMore,
    refresh,
    unblockUser,
    unblockLoading,
  };
}
