import { Layout, Menu } from 'antd';
import {
  DashboardOutlined,
  UnorderedListOutlined,
  FileTextOutlined,
  ReadOutlined,
  EditOutlined,
  SendOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { SIDER_WIDTH, HEADER_HEIGHT } from '@/utils/constants';

const menuItems = [
  { key: '/', icon: <DashboardOutlined />, label: '仪表盘' },
  { key: '/tasks', icon: <UnorderedListOutlined />, label: '任务管理' },
  { key: '/reports', icon: <FileTextOutlined />, label: '报告管理' },
  { key: '/news', icon: <ReadOutlined />, label: '新闻管理' },
  { key: '/drafts', icon: <EditOutlined />, label: '稿件管理' },
  { key: '/rss-sources', icon: <SendOutlined />, label: 'RSS 源' },
  { key: '/settings', icon: <SettingOutlined />, label: '设置' },
];

export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  // 选中当前路径匹配的菜单项
  const selectedKeys = [location.pathname === '/' ? '/' : `/${location.pathname.split('/')[1]}`];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider
        width={SIDER_WIDTH}
        style={{
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflow: 'auto',
          background: '#F8FAFC',
          borderRight: '1px solid #E5E7EB',
        }}
      >
        <div
          style={{
            height: HEADER_HEIGHT,
            display: 'flex',
            alignItems: 'center',
            padding: '0 16px',
            borderBottom: '1px solid #E5E7EB',
          }}
        >
          <SendOutlined style={{ fontSize: 20, color: '#2563EB' }} />
          <span
            style={{
              marginLeft: 10,
              fontSize: 16,
              fontWeight: 600,
              color: '#111827',
            }}
          >
            RSS Count
          </span>
        </div>
        <Menu
          mode="inline"
          selectedKeys={selectedKeys}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{
            background: 'transparent',
            borderRight: 'none',
            padding: '8px',
          }}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header
          style={{
            height: HEADER_HEIGHT,
            background: '#FFFFFF',
            borderBottom: '1px solid #E5E7EB',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <span style={{ fontSize: 14, color: '#6B7280' }}>
            RSS 新闻聚合与 AI 分析平台
          </span>
        </Layout.Header>
        <Layout.Content style={{ padding: 24, background: '#FFFFFF' }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
