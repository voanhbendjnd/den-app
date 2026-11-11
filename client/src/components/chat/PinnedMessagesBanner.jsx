import { useEffect, useRef, useState } from 'react';
import { Avatar, Button, Popconfirm, Spin, Tooltip, message as antMessage } from 'antd';
import {
  PushpinFilled,
  DownOutlined,
  UpOutlined,
  DeleteOutlined,
  AimOutlined,
} from '@ant-design/icons';
import { channelFeaturesApi } from '../../api/channelFeaturesApi';
import { getAuthorInfo, formatMessageTime } from '../../utils/channelMessages';
import {
  formatPinnedTitle,
  truncatePinnedContent,
  canUnpinMessage,
} from '../../utils/pinnedMessages';

export default function PinnedMessagesBanner({
  channelId,
  isOwner,
  members = [],
  currentUser = null,
  serverOwnerId = null,
  revision = 0,
  expanded = false,
  onToggleExpand,
  onJumpToMessage,
}) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [unpinningId, setUnpinningId] = useState(null);
  const [isExpandedLocal, setIsExpandedLocal] = useState(false);
  const requestId = useRef(0);
  const alive = useRef(true);

  const isExpanded = onToggleExpand !== undefined ? expanded : isExpandedLocal;
  const setExpanded = (nextValue) => {
    if (onToggleExpand) {
      onToggleExpand(nextValue);
    } else {
      setIsExpandedLocal(nextValue);
    }
  };

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      requestId.current++;
    };
  }, []);

  useEffect(() => {
    if (!channelId) {
      setItems([]);
      return;
    }

    const version = ++requestId.current;
    setLoading(true);

    channelFeaturesApi
      .pins(channelId)
      .then((res) => {
        if (!alive.current || version !== requestId.current) return;
        setItems(res?.items || []);
      })
      .catch(() => {
        if (!alive.current || version !== requestId.current) return;
        setItems([]);
      })
      .finally(() => {
        if (alive.current && version === requestId.current) {
          setLoading(false);
        }
      });
  }, [channelId, revision]);

  const handleUnpin = async (e, msgId) => {
    e?.stopPropagation?.();
    if (!msgId || unpinningId) return;

    setUnpinningId(msgId);
    try {
      await channelFeaturesApi.pin(msgId, false);
      if (!alive.current) return;
      setItems((prev) => prev.filter((item) => String(item.id) !== String(msgId)));
      antMessage.success('Đã bỏ ghim tin nhắn.');
    } catch {
      if (alive.current) {
        antMessage.error('Không thể bỏ ghim tin nhắn. Vui lòng thử lại.');
      }
    } finally {
      if (alive.current) {
        setUnpinningId(null);
      }
    }
  };

  if (!loading && items.length === 0) {
    return null;
  }

  const newest = items[0];
  const newestAuthor = newest
    ? getAuthorInfo(newest.senderId, members, currentUser, serverOwnerId)
    : null;
  const titleText = newest ? formatPinnedTitle(newestAuthor?.name) : 'Tin nhắn đã ghim';
  const previewText = newest ? truncatePinnedContent(newest.content, 120) : '';

  return (
    <div
      className={`pinned-banner ${isExpanded ? 'pinned-banner--expanded' : 'pinned-banner--collapsed'}`}
      role="region"
      aria-label="Thanh tin nhắn đã ghim"
    >
      {!isExpanded ? (
        /* Collapsed Bar: Height 48-56px matching mockup */
        <div className="pinned-banner__collapsed-bar">
          <div className="pinned-banner__left">
            <span className="pinned-banner__pin-icon-wrap" aria-hidden="true">
              <PushpinFilled className="pinned-banner__pin-icon" />
            </span>
            <div className="pinned-banner__info">
              <span className="pinned-banner__title">{titleText}</span>
              <span
                className="pinned-banner__preview"
                onClick={() => newest && onJumpToMessage && onJumpToMessage(newest.id)}
              >
                {previewText}
              </span>
            </div>
          </div>

          <div className="pinned-banner__right">
            <button
              type="button"
              className="pinned-banner__expand-btn"
              onClick={() => setExpanded(true)}
              aria-label="Xem tất cả tin nhắn đã ghim"
            >
              <span>Xem tất cả</span>
              <DownOutlined className="pinned-banner__chevron" />
            </button>
          </div>
        </div>
      ) : (
        /* Expanded View */
        <div className="pinned-banner__expanded-view">
          <div className="pinned-banner__header">
            <div className="pinned-banner__left">
              <span className="pinned-banner__pin-icon-wrap" aria-hidden="true">
                <PushpinFilled className="pinned-banner__pin-icon" />
              </span>
              <span className="pinned-banner__title">
                {titleText} ({items.length})
              </span>
            </div>
            <button
              type="button"
              className="pinned-banner__expand-btn"
              onClick={() => setExpanded(false)}
              aria-label="Thu gọn tin nhắn đã ghim"
            >
              <span>Thu gọn</span>
              <UpOutlined className="pinned-banner__chevron" />
            </button>
          </div>

          <div
            className="pinned-banner__list"
            role="list"
            aria-label="Danh sách tin nhắn đã ghim"
          >
            {loading && items.length === 0 ? (
              <div className="pinned-banner__loading">
                <Spin size="small" />
                <span>Đang tải tin nhắn đã ghim...</span>
              </div>
            ) : (
              items.map((msg) => {
                const author = getAuthorInfo(
                  msg.senderId,
                  members,
                  currentUser,
                  serverOwnerId
                );
                const timeStr = formatMessageTime(msg.createdAt);
                const showUnpin = canUnpinMessage(isOwner);

                return (
                  <div
                    key={msg.id}
                    className="pinned-banner__item"
                    role="listitem"
                    onClick={() => onJumpToMessage && onJumpToMessage(msg.id)}
                  >
                    <div className="pinned-banner__avatar-col">
                      <Avatar
                        size={30}
                        src={author.avatar}
                        className="pinned-banner__avatar"
                        style={{
                          background: author.isOwn
                            ? 'linear-gradient(135deg, #6366f1, #00f2fe)'
                            : 'linear-gradient(135deg, #3b82f6, #1d4ed8)',
                          fontWeight: 700,
                          fontSize: '12px',
                        }}
                      >
                        {author.initial}
                      </Avatar>
                    </div>

                    <div className="pinned-banner__item-body">
                      <div className="pinned-banner__meta">
                        <span className="pinned-banner__author-name">
                          {author.name}
                        </span>
                        {timeStr && (
                          <>
                            <span className="pinned-banner__dot">•</span>
                            <span className="pinned-banner__time">{timeStr}</span>
                          </>
                        )}
                      </div>
                      <div className="pinned-banner__content">{msg.content}</div>
                    </div>

                    <div
                      className="pinned-banner__item-actions"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <Tooltip title="Xem tin nhắn gốc" placement="top">
                        <Button
                          type="text"
                          size="small"
                          className="pinned-banner__action-icon-btn"
                          icon={<AimOutlined />}
                          onClick={() =>
                            onJumpToMessage && onJumpToMessage(msg.id)
                          }
                          aria-label="Cuộn đến tin nhắn gốc"
                        />
                      </Tooltip>

                      {showUnpin && (
                        <Popconfirm
                          title="Bỏ ghim tin nhắn?"
                          description="Bạn có chắc chắn muốn bỏ ghim tin nhắn này không?"
                          onConfirm={(e) => handleUnpin(e, msg.id)}
                          okText="Bỏ ghim"
                          cancelText="Hủy"
                          okButtonProps={{
                            danger: true,
                            loading: unpinningId === msg.id,
                          }}
                          placement="left"
                        >
                          <Tooltip title="Bỏ ghim" placement="top">
                            <Button
                              type="text"
                              size="small"
                              danger
                              className="pinned-banner__action-icon-btn pinned-banner__unpin-btn"
                              icon={<DeleteOutlined />}
                              loading={unpinningId === msg.id}
                              aria-label="Bỏ ghim tin nhắn"
                            />
                          </Tooltip>
                        </Popconfirm>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
