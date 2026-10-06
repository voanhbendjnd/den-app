import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

export default function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <Result
      status="403"
      title="403"
      subTitle="You do not have permission to access this page."
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          Go Home
        </Button>
      }
    />
  );
}
