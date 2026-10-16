import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { message } from 'antd';
import friendApi from '../api/friendApi';

const PAGE_SIZE = 20;

/**
 * Hook quản lý danh sách tất cả bạn bè đã kết bạn (status = ACCEPT).
 *
 * - Tải ban đầu qua POST /api/friendships/all-friend
 * - Cursor pagination (loadMore)
 * - Lọc theo searchQuery (username / name)
 * - Hành động: unfriend(friendId)
 *
 * @returns {{
 *   friends: FriendRequestProjection[],
 *   filteredFriends: FriendRequestProjection[],
 *   loading: boolean,
 *   hasMore: boolean,
 *   loadMore: function,
 *   refresh: function,
 *   searchQuery: string,
 *   setSearchQuery: function,
 *   unfriend: function,
 *   unfriendLoading: Object
 * }}
 */
export function useFriends() {
  const [friends, setFriends] = useState([]);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [unfriendLoading, setUnfriendLoading] = useState({});
  const [blockLoading, setBlockLoading] = useState({});

  const cursorRef = useRef(null);
  const loadedRef = useRef(false);

  // ── Fetch Friends ─────────────────────────────────────────────────────────
  const fetchFriends = useCallback(async (cursor = null, append = false) => {
    setLoading(true);
    try {
      const res = await friendApi.getAllFriends(PAGE_SIZE, cursor);
      const data = res.data?.data ?? res.data ?? {};
      const items = data.result ?? [];
      const nextCursor = data.nextCursor ?? null;
      const more = data.hasMore ?? false;

      cursorRef.current = nextCursor;
      setHasMore(more);

      if (append) {
        setFriends((prev) => {
          const existingIds = new Set(prev.map((f) => f.senderId));
          const unique = items.filter((f) => !existingIds.has(f.senderId));
          return [...prev, ...unique];
        });
      } else {
        setFriends(items);
      }
    } catch (err) {
      console.error('[useFriends] Load friends failed:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải lần đầu khi mount
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    fetchFriends(null, false);
  }, [fetchFriends]);

  // Load more (cursor pagination)
  const loadMore = useCallback(() => {
    if (!hasMore || loading) return;
    fetchFriends(cursorRef.current, true);
  }, [hasMore, loading, fetchFriends]);

  // Refresh
  const refresh = useCallback(() => {
    cursorRef.current = null;
    fetchFriends(null, false);
  }, [fetchFriends]);

  // Lắng nghe realtime khi có lời mời kết bạn được chấp nhận
  useEffect(() => {
    const handleAccepted = () => {
      refresh();
    };
    window.addEventListener('denhub:friend-request-accepted', handleAccepted);
    return () => {
      window.removeEventListener('denhub:friend-request-accepted', handleAccepted);
    };
  }, [refresh]);

  // Lọc theo searchQuery
  const filteredFriends = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return friends;
    return friends.filter(
      (f) =>
        f.username?.toLowerCase().includes(q) ||
        f.name?.toLowerCase().includes(q)
    );
  }, [friends, searchQuery]);

  // ── Hủy kết bạn (unfriend) ─────────────────────────────────────────────────
  const unfriend = useCallback(async (friendId) => {
    setUnfriendLoading((prev) => ({ ...prev, [friendId]: true }));
    try {
      await friendApi.removeFriend(friendId);
      // Xóa người bạn này khỏi danh sách bạn bè
      setFriends((prev) => prev.filter((f) => f.senderId !== friendId));
      message.success('Đã hủy kết bạn thành công.');
    } catch (err) {
      console.error('[useFriends] unfriend failed:', err);
      const errMsg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        'Không thể hủy kết bạn. Vui lòng thử lại!';
      message.error(errMsg);
    } finally {
      setUnfriendLoading((prev) => {
        const next = { ...prev };
        delete next[friendId];
        return next;
      });
    }
  }, []);

  // ── Chặn người dùng (block user) ───────────────────────────────────────────
  const blockUser = useCallback(async (friendId) => {
    setBlockLoading((prev) => ({ ...prev, [friendId]: true }));
    try {
      await friendApi.blockUser(friendId);
      // Xóa người này khỏi danh sách bạn bè
      setFriends((prev) => prev.filter((f) => f.senderId !== friendId));
      message.success('Đã chặn người dùng thành công.');
    } catch (err) {
      console.error('[useFriends] blockUser failed:', err);
      const errMsg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        'Không thể chặn người dùng. Vui lòng thử lại!';
      message.error(errMsg);
    } finally {
      setBlockLoading((prev) => {
        const next = { ...prev };
        delete next[friendId];
        return next;
      });
    }
  }, []);

  return {
    friends,
    filteredFriends,
    loading,
    hasMore,
    loadMore,
    refresh,
    searchQuery,
    setSearchQuery,
    unfriend,
    unfriendLoading,
    blockUser,
    blockLoading,
  };
}
