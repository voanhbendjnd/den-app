import { Spin } from 'antd';

export default function PageLoading({ tip = 'Loading...' }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '40vh',
      }}
    >
      <Spin size="large" tip={tip} />
    </div>
  );
}
