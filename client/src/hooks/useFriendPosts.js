import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { message } from 'antd';
import postApi from '../api/postApi';
import { getAccessToken } from '../utils/token';
import { useAuth } from './useAuth';

const DEFAULT_PAGE_SIZE = 10;
const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

/**
 * Custom hook quản lý Bảng tin bài viết của bạn bè.
 * Hỗ trợ cursor pagination (cursorDate + cursorId), refresh, optimistic like,
 * và tích hợp WebSocket STOMP real-time cho bài viết mới (/app/post & /user/queue/new-post).
 */
export function useFriendPosts(pageSize = DEFAULT_PAGE_SIZE) {
  const { currentUser } = useAuth();
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState(null);
  const [wsConnected, setWsConnected] = useState(false);

  const cursorDateRef = useRef(null);
  const cursorIdRef = useRef(null);
  const loadedRef = useRef(false);
  const stompRef = useRef(null);
  const subscriptionRef = useRef(null);

  // Tải danh sách bài viết từ REST API
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

  // Kết nối STOMP WebSocket và lắng nghe bài viết mới từ bạn bè
  useEffect(() => {
    const token = getAccessToken();
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      onConnect: () => {
        setWsConnected(true);
        // Subscribe vào kênh nhận bài viết mới realtime
        subscriptionRef.current = client.subscribe('/user/queue/new-post', (frame) => {
          try {
            const newPost = JSON.parse(frame.body);
            if (!newPost || !newPost.id) return;

            setPosts((prev) => {
              // Deduplicate
              if (prev.some((p) => p.id === newPost.id)) return prev;
              return [newPost, ...prev];
            });
          } catch (err) {
            console.error('[useFriendPosts] Failed to parse realtime post:', err);
          }
        });
      },
      onDisconnect: () => {
        setWsConnected(false);
      },
      onStompError: (frame) => {
        console.error('[useFriendPosts] STOMP error:', frame);
      },
    });

    stompRef.current = client;
    client.activate();

    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
        subscriptionRef.current = null;
      }
      if (stompRef.current) {
        stompRef.current.deactivate();
        stompRef.current = null;
      }
      setWsConnected(false);
    };
  }, []);

  // Đăng bài viết mới qua STOMP WebSocket
  const createPost = useCallback(
    async ({ content, privacy = 'PUBLIC', mediaUrl = null }) => {
      if (!stompRef.current || !stompRef.current.connected) {
        throw new Error('Chưa kết nối tới máy chủ thời gian thực. Vui lòng thử lại sau giây lát.');
      }

      const payload = {
        content: content?.trim() || null,
        privacy: privacy || 'PUBLIC',
        mediaUrl: mediaUrl || null,
      };

      stompRef.current.publish({
        destination: '/app/post',
        body: JSON.stringify(payload),
      });

      // Optimistic update: đưa bài viết của tác giả lên đầu feed ngay lập tức
      const localPost = {
        id: Date.now(), // ID tạm thời
        userId: currentUser?.id,
        authorName: currentUser?.displayName || currentUser?.username || currentUser?.name || 'Bạn',
        authorUsername: currentUser?.username,
        authorAvatar: currentUser?.avatar,
        content: payload.content,
        privacy: payload.privacy,
        likeCounts: 0,
        mediaUrl: payload.mediaUrl,
        createdDate: new Date().toISOString(),
        isLiked: false,
      };

      setPosts((prev) => [localPost, ...prev]);
    },
    [currentUser]
  );

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

  // Optimistic like toggle kết nối REST API /posts/{postId}/like với rollback khi lỗi
  const toggleLike = useCallback(async (postId) => {
    let previousPost = null;

    // 1. Optimistic update trên giao diện ngay tức thì
    setPosts((prev) =>
      prev.map((post) => {
        if (post.id !== postId) return post;
        previousPost = { ...post };
        const isLiked = post.isLiked ?? false;
        const currentLikes = post.likeCounts ?? 0;
        return {
          ...post,
          isLiked: !isLiked,
          likeCounts: isLiked ? Math.max(0, currentLikes - 1) : currentLikes + 1,
        };
      })
    );

    // 2. Gửi request lên backend API
    try {
      const res = await postApi.toggleLike(postId);
      const data = res.data?.data ?? res.data;
      if (data) {
        setPosts((prev) =>
          prev.map((post) => {
            if (post.id !== postId) return post;
            return {
              ...post,
              isLiked: typeof data.liked === 'boolean' ? data.liked : post.isLiked,
              likeCounts: typeof data.totalLikes === 'number' ? data.totalLikes : post.likeCounts,
            };
          })
        );
      }
    } catch (err) {
      console.error('[useFriendPosts] Failed to toggle like:', err);
      // 3. Rollback nếu server trả lỗi
      if (previousPost) {
        setPosts((prev) =>
          prev.map((post) => (post.id === postId ? previousPost : post))
        );
      }
      message.error(err.response?.data?.message || 'Không thể thao tác thích bài viết. Vui lòng thử lại.');
    }
  }, []);

  return {
    posts,
    loading,
    loadingMore,
    hasMore,
    error,
    wsConnected,
    refresh,
    loadMore,
    toggleLike,
    createPost,
  };
}

