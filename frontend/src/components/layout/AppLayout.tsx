import { Button, Input, Tooltip } from 'antd';
import {
  AppstoreOutlined,
  CheckSquareOutlined,
  EditOutlined,
  FileTextOutlined,
  MoonOutlined,
  PlusOutlined,
  ReadOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
  WifiOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import './AppLayout.css';

const navItems = [
  { key: '/', icon: <AppstoreOutlined />, label: '首页' },
  { key: '/news', icon: <ReadOutlined />, label: '资讯' },
  { key: '/reports', icon: <FileTextOutlined />, label: '报告' },
  { key: '/tasks', icon: <CheckSquareOutlined />, label: '任务' },
  { key: '/drafts', icon: <EditOutlined />, label: '稿件' },
  { key: '/rss-sources', icon: <WifiOutlined />, label: 'RSS' },
];

const pageTitles: Record<string, string> = {
  '/': '今日工作台',
  '/news': '资讯工作台',
  '/reports': '报告管理',
  '/tasks': '任务管理',
  '/drafts': '稿件管理',
  '/rss-sources': 'RSS 源管理',
  '/settings': '设置',
};

export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [darkMode, setDarkMode] = useState(false);

  const activePath = useMemo(() => {
    if (location.pathname === '/') return '/';
    return `/${location.pathname.split('/')[1]}`;
  }, [location.pathname]);

  const pageTitle = pageTitles[activePath] ?? 'RSS Count';
  const isNewsWorkspace = activePath === '/news';

  useEffect(() => {
    document.documentElement.dataset.theme = darkMode ? 'dark' : 'light';
    return () => {
      delete document.documentElement.dataset.theme;
    };
  }, [darkMode]);

  return (
    <div className="app-shell">
      <aside className="app-rail" aria-label="主导航">
        <button className="app-logo" onClick={() => navigate('/')} aria-label="RSS Count 首页">
          RC
        </button>

        <nav className="app-rail-nav">
          {navItems.map((item) => (
            <Tooltip key={item.key} title={item.label} placement="right">
              <button
                className={`app-rail-item ${activePath === item.key ? 'is-active' : ''}`}
                onClick={() => navigate(item.key)}
                aria-label={item.label}
              >
                <span className="app-rail-icon">{item.icon}</span>
                <span>{item.label}</span>
              </button>
            </Tooltip>
          ))}
        </nav>

        <Tooltip title="设置" placement="right">
          <button
            className={`app-rail-item app-rail-settings ${
              activePath === '/settings' ? 'is-active' : ''
            }`}
            onClick={() => navigate('/settings')}
            aria-label="设置"
          >
            <span className="app-rail-icon"><SettingOutlined /></span>
            <span>设置</span>
          </button>
        </Tooltip>
      </aside>

      <header className="app-topbar">
        <h1>{pageTitle}</h1>

        <Input
          className="app-global-search"
          prefix={<SearchOutlined />}
          placeholder="搜索标题、来源或正文"
          suffix={<kbd>⌘ K</kbd>}
          onPressEnter={(event) => {
            const keyword = event.currentTarget.value.trim();
            if (keyword) navigate(`/news?q=${encodeURIComponent(keyword)}`);
          }}
        />

        <div className="app-topbar-actions">
          <Tooltip title={darkMode ? '切换浅色模式' : '切换深色模式'}>
            <Button
              className="app-icon-button"
              icon={darkMode ? <SunOutlined /> : <MoonOutlined />}
              onClick={() => setDarkMode((value) => !value)}
            />
          </Tooltip>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/tasks')}
          >
            创建拉取任务
          </Button>
        </div>
      </header>

      <main className={`app-content ${isNewsWorkspace ? 'app-content--workspace' : ''}`}>
        <Outlet />
      </main>
    </div>
  );
}
