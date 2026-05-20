import { createBrowserRouter } from 'react-router-dom';
import AppLayout from './components/layout/AppLayout';
import Dashboard from './pages/Dashboard';
import TaskManagement from './pages/TaskManagement';
import TaskDetail from './pages/TaskDetail';
import ReportManagement from './pages/ReportManagement';
import ReportDetail from './pages/ReportDetail';
import NewsManagement from './pages/NewsManagement';
import DraftManagement from './pages/DraftManagement';
import RssSourceManagement from './pages/RssSourceManagement';
import Settings from './pages/Settings';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: 'tasks', element: <TaskManagement /> },
      { path: 'tasks/:id', element: <TaskDetail /> },
      { path: 'reports', element: <ReportManagement /> },
      { path: 'reports/:id', element: <ReportDetail /> },
      { path: 'news', element: <NewsManagement /> },
      { path: 'drafts', element: <DraftManagement /> },
      { path: 'drafts/:id', element: <DraftManagement /> },
      { path: 'rss-sources', element: <RssSourceManagement /> },
      { path: 'settings', element: <Settings /> },
    ],
  },
]);
