import { useEffect, useRef, useState } from 'react';
import { Button, Drawer, Input, Alert, Spin, Tooltip } from 'antd';
import {
  SearchOutlined,
  BookOutlined,
  BellOutlined,
  BellFilled,
} from '@ant-design/icons';
import { channelFeaturesApi as api } from '../../api/channelFeaturesApi';
import { sortMessagesChronological } from '../../utils/channelMessages';

export default function ChannelTools(props) {
  return <ChannelToolsSession key={props.channelId} {...props} />;
}

function ChannelToolsSession({
  channelId,
  isOwner,
  revision,
  onPreference,
  onJumpToMessage,
}) {
  const [panel, setPanel] = useState(null);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState({ items: [], nextCursor: null, hasMore: false });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [muted, setMuted] = useState(false);
  const [preferenceReady, setPreferenceReady] = useState(false);
  const [preferenceError, setPreferenceError] = useState('');
  const [busy, setBusy] = useState(false);
  const [preferenceBusy, setPreferenceBusy] = useState(false);
  const [preferenceAttempt, setPreferenceAttempt] = useState(0);
  const request = useRef(0);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      request.current++;
    };
  }, []);

  useEffect(() => {
    let current = true;
    api
      .preference(channelId)
      .then((value) => {
        if (!current) return;
        setMuted(Boolean(value.muted));
        setPreferenceReady(true);
        setPreferenceError('');
        onPreference(Boolean(value.muted));
      })
      .catch(() => {
        if (current) {
          setPreferenceReady(false);
          setPreferenceError('Chưa tải được lựa chọn thông báo.');
        }
      });
    return () => {
      current = false;
    };
  }, [channelId, revision, onPreference, preferenceAttempt]);

  async function load(before = null) {
    const version = ++request.current;
    if (!panel || (panel === 'search' && !query.trim())) {
      setPage({ items: [] });
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const result =
        panel === 'search'
          ? await api.search(channelId, query.trim(), before)
          : await api.saved(before);
      if (!alive.current || version !== request.current) return;
      setPage((previous) => ({
        ...result,
        items: before
          ? sortMessagesChronological([...(result.items || []), ...previous.items]).reverse()
          : result.items || [],
      }));
    } catch {
      if (alive.current && version === request.current) {
        setPage({ items: [] });
        setError('Không tải được tin nhắn. Hãy thử lại.');
      }
    } finally {
      if (alive.current && version === request.current) setLoading(false);
    }
  }

  useEffect(() => {
    request.current++;
    const timer = setTimeout(() => load(), 200);
    return () => {
      clearTimeout(timer);
      request.current++;
    };
  }, [panel, query, revision, channelId]);

  async function toggleNotification() {
    setPreferenceBusy(true);
    setPreferenceError('');
    try {
      const result = await api.setPreference(channelId, !muted);
      if (alive.current) {
        setMuted(result.muted);
        setPreferenceReady(true);
        onPreference(result.muted);
      }
    } catch {
      if (alive.current) setPreferenceError('Chưa đổi được thông báo. Hãy thử lại.');
    } finally {
      if (alive.current) setPreferenceBusy(false);
    }
  }

  async function remove(item) {
    setBusy(true);
    setError('');
    try {
      await api.save(item.id, false);
      if (alive.current) await load();
    } catch {
      if (alive.current) setError('Chưa thực hiện được thao tác. Hãy thử lại.');
    } finally {
      if (alive.current) setBusy(false);
    }
  }

  return (
    <>
      <Tooltip title="Tìm kiếm tin nhắn" placement="bottom">
        <Button
          type="text"
          className="group-header-tool-btn"
          icon={<SearchOutlined />}
          aria-label="Tìm kiếm tin nhắn"
          onClick={() => setPanel('search')}
        />
      </Tooltip>

      <Tooltip title="Tin đã lưu" placement="bottom">
        <Button
          type="text"
          className="group-header-tool-btn"
          icon={<BookOutlined />}
          aria-label="Tin đã lưu"
          onClick={() => setPanel('saved')}
        />
      </Tooltip>

      <Tooltip
        title={muted ? 'Bật thông báo kênh' : 'Tắt thông báo kênh'}
        placement="bottom"
      >
        <Button
          type="text"
          className={`group-header-tool-btn ${
            muted ? 'group-header-tool-btn--muted' : 'group-header-tool-btn--active'
          }`}
          icon={
            muted ? (
              <BellOutlined style={{ opacity: 0.55 }} />
            ) : (
              <BellFilled style={{ color: '#818cf8' }} />
            )
          }
          loading={preferenceBusy}
          disabled={!preferenceReady || preferenceBusy}
          aria-label={muted ? 'Bật thông báo kênh' : 'Tắt thông báo kênh'}
          onClick={toggleNotification}
        />
      </Tooltip>

      <Drawer
        title={panel === 'search' ? 'Tìm kiếm tin nhắn' : 'Tin đã lưu của bạn'}
        open={Boolean(panel)}
        onClose={() => setPanel(null)}
        width={420}
      >
        {panel === 'search' && (
          <Input
            aria-label="Từ khóa tìm tin nhắn"
            value={query}
            maxLength={200}
            placeholder="Nhập nội dung cần tìm"
            onChange={(event) => setQuery(event.target.value)}
          />
        )}
        {error && (
          <Alert
            type="error"
            message={error}
            action={<Button onClick={() => load()}>Thử lại</Button>}
          />
        )}
        {loading && <Spin aria-label="Đang tải kết quả" />}
        {!loading && !error && page.items.length === 0 && (
          <p>
            {panel === 'search' && !query.trim()
              ? 'Nhập từ khóa để tìm trong kênh này.'
              : 'Chưa có tin nhắn phù hợp.'}
          </p>
        )}
        <div role="list" aria-label="Kết quả công cụ tin nhắn">
          {page.items.map((item) => (
            <article
              key={item.id}
              role="listitem"
              className="channel-tool-result"
              onClick={() => {
                if (onJumpToMessage) {
                  onJumpToMessage(item.id);
                  setPanel(null);
                }
              }}
              style={{ cursor: onJumpToMessage ? 'pointer' : 'default' }}
            >
              <small>
                Kênh #{item.channelId} ·{' '}
                {item.senderId === undefined ? '' : `Thành viên #${item.senderId}`}
              </small>
              <p>{item.content}</p>
              {panel === 'saved' && (
                <Button
                  size="small"
                  disabled={busy}
                  onClick={(e) => {
                    e.stopPropagation();
                    remove(item);
                  }}
                >
                  Bỏ lưu
                </Button>
              )}
            </article>
          ))}
        </div>
        {page.hasMore && (
          <Button disabled={loading} onClick={() => load(page.nextCursor)}>
            Tải thêm kết quả
          </Button>
        )}
      </Drawer>
    </>
  );
}
