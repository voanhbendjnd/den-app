import { Result, Button } from 'antd';

export default function PageError({ message = 'Something went wrong.', onRetry }) {
  return (
    <Result
      status="error"
      title="Error"
      subTitle={message}
      extra={
        onRetry && (
          <Button type="primary" onClick={onRetry}>
            Try Again
          </Button>
        )
      }
    />
  );
}
