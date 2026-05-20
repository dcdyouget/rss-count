import { Steps } from 'antd';
import {
  CloudDownloadOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { theme } from 'antd';

interface ThreeStepProgressProps {
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  currentPhase?: 'FETCHING' | 'FORMATTING';
}

const THREE_STEPS = [
  {
    title: '拉取新闻中',
    icon: <CloudDownloadOutlined />,
  },
  {
    title: '格式化新闻中',
    icon: <FileTextOutlined />,
  },
  {
    title: '完成',
    icon: <CheckCircleOutlined />,
  },
];

/**
 * ThreeStepProgress — 三节点任务进度指示器
 *
 * 用于任务详情 Drawer 展示当前任务执行阶段：
 * - RUNNING + FETCHING  → step1 进行中（蓝色脉动），step2/3 等候
 * - RUNNING + FORMATTING → step1 完成（绿色），step2 进行中，step3 等候
 * - COMPLETED → 全部完成（绿色）
 * - FAILED → step1/2 完成，step3 错误（红色）
 */
export default function ThreeStepProgress({ status, currentPhase }: ThreeStepProgressProps) {
  const { token } = theme.useToken();

  // Determine current step index and per-step status
  let current = 0;
  let stepStatuses: ('wait' | 'process' | 'finish' | 'error')[] = ['wait', 'wait', 'wait'];

  if (status === 'RUNNING') {
    if (!currentPhase || currentPhase === 'FETCHING') {
      current = 0;
      stepStatuses = ['process', 'wait', 'wait'];
    } else if (currentPhase === 'FORMATTING') {
      current = 1;
      stepStatuses = ['finish', 'process', 'wait'];
    }
  } else if (status === 'COMPLETED') {
    current = 2;
    stepStatuses = ['finish', 'finish', 'finish'];
  } else if (status === 'FAILED') {
    current = 2;
    stepStatuses = ['finish', 'finish', 'error'];
  }

  const items = THREE_STEPS.map((step, index) => ({
    title: step.title,
    icon: step.icon,
    status: stepStatuses[index],
  }));

  return (
    <Steps
      current={current}
      items={items}
      size="small"
      style={{
        padding: token.paddingMD,
      }}
    />
  );
}
