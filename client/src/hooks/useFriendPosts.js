import { useState, useEffect, useRef, useCallback } from 'react';
import postApi from '../api/postApi';

const DEFAULT_PAGE_SIZE = 10;

/**
 * Custom hook quản lý Bảng tin bài viết của bạn bè.
 * Hỗ trợ cursor pagination (cursorDate + cursorId), refresh, và optimistic like.
 */
export function useFriendPosts(pageSize = DEFAULT_PAGE_SIZE) {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState(null);

  const cursorDateRef = useRef(null);
  const cursorIdRef = useRef(null);
  const loadedRef = useRef(false);

  // Tải danh sách bài viết
  const fetchPosts = useCallback(
    async (append = false) => {
      if (append) {
        setLoadingMore(true);
      } else {
        setLoading(true);
        setError(null);
      }

      try {
        const cDate = append ? cursorDateRef.current : null;
        const cId = append ? cursorIdRef.current : null;

        const res = await postApi.getAllPostsFromFriends(pageSize, cDate, cId);
        const data = res.data?.data ?? res.data ?? {};
        const items = data.result ?? [];
        const nextDate = data.cursorDate ?? null;
        const nextId = data.cursorId ?? null;
        const more = Boolean(data.hasMore);

        cursorDateRef.current = nextDate;
        cursorIdRef.current = nextId;
        setHasMore(more);

        if (append) {
          setPosts((prev) => {
            const existingIds = new Set(prev.map((p) => p.id));
            const newItems = items.filter((p) => !existingIds.has(p.id));
            return [...prev, ...newItems];
          });
        } else {
          setPosts(items);
        }
      } catch (err) {
        console.error('[useFriendPosts] Failed to fetch posts:', err);
        setError('Không thể tải bài viết. Vui lòng thử lại sau.');
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [pageSize]
  );

  // Tải lần đầu khi mount
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    fetchPosts(false);
  }, [fetchPosts]);

  // Tải thêm bài viết (Cursor pagination)
  const loadMore = useCallback(() => {
    if (!hasMore || loading || loadingMore) return;
    fetchPosts(true);
  }, [hasMore, loading, loadingMore, fetchPosts]);

  // Làm mới bảng tin
  const refresh = useCallback(() => {
    cursorDateRef.current = null;
    cursorIdRef.current = null;
    fetchPosts(false);
  }, [fetchPosts]);

  // Optimistic like toggle
  const toggleLike = useCallback((postId) => {
    setPosts((prev) =>
      prev.map((post) => {
        if (post.id !== postId) return post;
        const isLiked = post.isLiked ?? false;
        const currentLikes = post.likeCounts ?? 0;
        return {
          ...post,
          isLiked: !isLiked,
          likeCounts: isLiked ? Math.max(0, currentLikes - 1) : currentLikes + 1,
        };
      })
    );
  }, []);

  return {
    posts,
    loading,
    loadingMore,
    hasMore,
    error,
    refresh,
    loadMore,
    toggleLike,
  };
}
