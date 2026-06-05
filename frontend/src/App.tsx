import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const themeConfig = {
  token: {
    colorPrimary: '#316BFF',
    colorSuccess: '#12A66A',
    colorWarning: '#E98A15',
    colorError: '#EF4444',
    colorInfo: '#316BFF',
    colorTextBase: '#17202A',
    colorBgBase: '#FFFFFF',
    colorBgLayout: '#F4F6F8',
    colorBorder: '#E7EAF0',

    borderRadius: 8,
    borderRadiusSM: 6,
    borderRadiusLG: 12,

    fontSize: 14,
    fontSizeHeading1: 28,
    fontSizeHeading2: 22,
    fontSizeHeading3: 18,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    lineHeight: 1.6,

    controlHeight: 36,
    paddingContentHorizontal: 24,
    paddingContentVertical: 24,
  },
  components: {
    Card: {
      borderRadiusLG: 12,
      paddingLG: 24,
    },
    Table: {
      headerBg: '#F8F9FB',
      borderColor: '#E7EAF0',
      borderRadius: 8,
    },
    Button: {
      borderRadius: 8,
    },
    Input: {
      borderRadius: 8,
    },
    Select: {
      borderRadius: 8,
    },
    Menu: {
      itemBorderRadius: 8,
      itemHeight: 40,
      collapsedWidth: 64,
    },
    Drawer: {
      paddingLG: 24,
    },
    Tag: {
      borderRadiusSM: 6,
    },
  },
};

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={themeConfig} locale={zhCN}>
        <RouterProvider router={router} />
      </ConfigProvider>
    </QueryClientProvider>
  );
}
