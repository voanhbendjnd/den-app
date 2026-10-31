import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Alert, Avatar, Button, Empty, Input, Pagination, Skeleton } from 'antd';
import { CompassOutlined, SearchOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate, useOutletContext } from 'react-router-dom';
import serverApi from '../../api/serverApi';
import './discovery.css';

export default function DiscoverServersPage() {
  const { setServers, refreshServers, openJoinModal } = useOutletContext();
  const navigate = useNavigate();
  const location = useLocation();
  const visit = useMemo(() => ({}), [location.key]);
  const activeVisit = useRef(visit);
  useLayoutEffect(() => {
    activeVisit.current = visit;
    return () => { activeVisit.current = null; };
  }, [visit]);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [joinError, setJoinError] = useState(null);
  const [joining, setJoining] = useState(null);
  const joinPending = useRef(false);
  const requests = useRef(0);

  useEffect(() => {
    const request = ++requests.current;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    const timer = setTimeout(async () => {
      try {
        const response = await serverApi.discover({ q: query.trim(), page, size: 20 }, controller.signal);
        if (requests.current !== request) return;
        const data = response.data?.data;
        if (!data || !Array.isArray(data.items) || !Number.isInteger(data.totalElements)) {
          throw new Error('Invalid discovery response');
        }
        setResult(data);
      } catch {
        if (requests.current === request) setError('Chưa tải được danh sách server. Vui lòng thử lại.');
      } finally {
        if (requests.current === request) setLoading(false);
      }
    }, query.trim() ? 300 : 0);
    return () => { requests.current += 1; clearTimeout(timer); controller.abort(); };
  }, [query, page, retry]);

  const changeSearch = value => {
    // Invalidate synchronously: an older response must not win before effect cleanup.
    requests.current += 1;
    setQuery(value);
    setPage(0);
    setLoading(true);
    setJoinError(null);
  };

  const join = async server => {
    if (joinPending.current) return;
    const originVisit = visit;
    const originUrl = window.location.href;
    joinPending.current = true;
    setJoining(server.id);
    setJoinError(null);
    try {
      await serverApi.join(server.id);
      // A committed membership still updates the shared list after leaving this page.
      setServers(previous => previous.some(item => String(item.id) === String(server.id)) ? previous
        : [{ id: server.id, name: server.name, iconUrl: server.iconUrl, visibility: 'PUBLIC' }, ...previous]);
      refreshServers({ background: true });
      if (activeVisit.current === originVisit && window.location.href === originUrl) {
        navigate(`/servers/${server.id}`);
      }
    } catch (failure) {
      if (activeVisit.current !== originVisit || window.location.href !== originUrl) return;
      const status = failure.response?.status;
      setJoinError(status === 403 ? 'Server hiện yêu cầu mã mời. Hãy tải lại danh sách hoặc nhập mã mời.'
        : status === 404 ? 'Server không còn tồn tại. Hãy tải lại danh sách.'
          : status === 400 ? 'Không thể tham gia. Bạn có thể đã là thành viên; hãy tải lại danh sách.'
            : 'Không thể xác nhận đã tham gia server. Vui lòng tải lại danh sách hoặc thử lại.');
    } finally {
      joinPending.current = false;
      if (activeVisit.current === originVisit) setJoining(null);
    }
  };

  return (
    <main className="discovery-page">
      <header className="discovery-header">
        <div><h1>Khám phá server</h1><p>Tìm cộng đồng bạn muốn tham gia.</p></div>
        <Link to="/">Server của bạn</Link>
      </header>
      <label htmlFor="discover-server-search">Tìm server công khai</label>
      <Input id="discover-server-search" size="large" prefix={<SearchOutlined />} allowClear maxLength={100}
        placeholder="Nhập tên server…" value={query} onChange={event => changeSearch(event.target.value)} />
      <p className="discovery-hint">Tìm server công khai bạn chưa tham gia. Server của bạn không xuất hiện ở đây.</p>
      {joinError && <Alert type="error" showIcon message={joinError} role="alert" action={
        <Button onClick={() => { requests.current += 1; setLoading(true); setRetry(value => value + 1); setJoinError(null); }}>Tải lại danh sách</Button>
      } />}
      {loading ? <div role="status" aria-label="Đang tìm server"><Skeleton active /><Skeleton active /></div>
        : error ? <Alert type="error" showIcon message={error} role="alert"
          action={<Button onClick={() => setRetry(value => value + 1)}>Thử lại</Button>} />
          : result?.items.length === 0 ? <Empty description={query.trim()
            ? 'Không tìm thấy server phù hợp' : 'Chưa có server mới để khám phá'}>
            {query.trim() && <Button onClick={() => changeSearch('')}>Xóa tìm kiếm</Button>}
          </Empty> : <>
            <p role="status">Tìm thấy {result?.totalElements || 0} server</p>
            <ul className="discovery-results" aria-label="Kết quả tìm server">
              {result?.items.map(server => <li key={server.id}>
                <Avatar shape="square" size={48} src={server.iconUrl}>{server.name?.[0]?.toUpperCase()}</Avatar>
                <div className="discovery-server-info"><strong title={server.name}>{server.name}</strong>
                  <span>Server công khai</span></div>
                <Button type="primary" aria-label="Tham gia" loading={joining === server.id} disabled={joining !== null}
                  onClick={() => join(server)}>Tham gia</Button>
              </li>)}
            </ul>
            <Pagination current={page + 1} pageSize={20} total={result?.totalElements || 0} hideOnSinglePage showSizeChanger={false}
              onChange={next => { requests.current += 1; setLoading(true); setPage(next - 1); }} />
          </>}
      <footer className="discovery-footer"><CompassOutlined /><span>Có mã mời hoặc ID server công khai?</span>
        <Button onClick={openJoinModal}>Nhập mã mời / ID</Button></footer>
    </main>
  );
}
