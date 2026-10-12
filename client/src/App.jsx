import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider, App as AntApp, theme as antTheme } from 'antd';
import { AuthProvider } from './contexts/AuthContext';
import AppRoutes from './routes/AppRoutes';

const theme = {
  algorithm: antTheme.darkAlgorithm,
  token: {
    colorPrimary: '#6366F1',
    colorInfo: '#00F2FE',
    colorSuccess: '#10B981',
    borderRadius: 10,
    colorBgBase: '#080B11',
    colorBgContainer: '#151D2E',
    colorBgLayout: '#0C101A',
    colorBgElevated: '#1C263B',
    colorBorder: 'rgba(255, 255, 255, 0.08)',
    colorBorderSecondary: 'rgba(255, 255, 255, 0.04)',
    colorTextBase: '#F1F5F9',
    colorTextSecondary: '#94A3B8',
    colorTextTertiary: '#64748B',
    fontFamily: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
  },
  components: {
    Layout: {
      siderBg: '#0F1422',
      headerBg: '#151D2E',
      bodyBg: '#0C101A',
    },
    Menu: {
      itemBorderRadius: 10,
      itemBg: 'transparent',
      itemHoverBg: 'rgba(99, 102, 241, 0.1)',
      itemSelectedBg: 'rgba(99, 102, 241, 0.2)',
      itemSelectedColor: '#00F2FE',
      itemColor: '#94A3B8',
    },
    Card: {
      colorBgContainer: '#151D2E',
      colorBorderSecondary: 'rgba(255, 255, 255, 0.06)',
      borderRadiusLG: 14,
    },
    Button: {
      colorPrimary: '#6366F1',
      colorPrimaryHover: '#4F46E5',
      borderRadius: 10,
      controlHeight: 40,
    },
    Input: {
      colorBgContainer: '#0F1523',
      colorBorder: 'rgba(255, 255, 255, 0.09)',
      borderRadius: 10,
      colorTextPlaceholder: '#475569',
    },
    Modal: {
      contentBg: '#151D2E',
      headerBg: '#151D2E',
    }
  },
};

export default function App() {
  return (
    <ConfigProvider theme={theme}>
      <AntApp>
        <BrowserRouter>
          <AuthProvider>
            <AppRoutes />
          </AuthProvider>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}
