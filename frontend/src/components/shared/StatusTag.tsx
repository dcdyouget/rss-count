import React from 'react';
import { Tag, theme } from 'antd';
import {
  SyncOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import type { TaskStatus } from '@/types';
import '../../styles/components.css';

interface StatusTagProps {
  status: TaskStatus;
  showIcon?: boolean;
}

const statusConfig: Record<
  TaskStatus,
  { text: string; color: string; icon: React.ReactNode }
> = {
  RUNNING: {
    text: '执行中',
    color: '#2563EB',
    icon: <SyncOutlined spin />,
  },
  COMPLETED: {
    text: '已完成',
    color: '#10B981',
    icon: <CheckCircleOutlined />,
  },
  FAILED: {
    text: '失败',
    color: '#EF4444',
    icon: <CloseCircleOutlined />,
  },
};

export const StatusTag: React.FC<StatusTagProps> = ({
  status,
  showIcon = true,
}) => {
  const { token } = theme.useToken();

  // Map status to Ant Design token colors
  const colorMap: Record<TaskStatus, string> = {
    RUNNING: token.colorInfo,
    COMPLETED: token.colorSuccess,
    FAILED: token.colorError,
  };

  const config = statusConfig[status];
  const tagColor = colorMap[status];
  const isRunning = status === 'RUNNING';

  return (
    <Tag
      color={tagColor}
      icon={showIcon ? config.icon : undefined}
      className={isRunning ? 'status-tag--running' : undefined}
    >
      {config.text}
    </Tag>
  );
};
