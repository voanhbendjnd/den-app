import { Empty } from 'antd';

export default function EmptyState({ description = 'No data yet.' }) {
  return (
    <div style={{ padding: '48px 0', textAlign: 'center' }}>
      <Empty description={description} />
    </div>
  );
}
