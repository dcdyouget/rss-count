import type { DraftStyle, DraftPlatform, TaskStatus } from '@/types';

// ---------- 任务状态 ----------

export const TASK_STATUS_MAP: Record<TaskStatus, { label: string; color: string }> = {
  RUNNING: { label: '进行中', color: 'blue' },
  COMPLETED: { label: '已完成', color: 'green' },
  FAILED: { label: '失败', color: 'red' },
};

// ---------- 稿件风格 ----------

export const DRAFT_STYLES: DraftStyle[] = [
  { label: '正式', value: '正式' },
  { label: '轻松', value: '轻松' },
  { label: '客观', value: '客观' },
  { label: '犀利', value: '犀利' },
  { label: '幽默', value: '幽默' },
];

// ---------- 目标平台 ----------

export const DRAFT_PLATFORMS: DraftPlatform[] = [
  { label: '知乎', value: '知乎' },
  { label: '今日头条', value: '今日头条' },
  { label: '小红书', value: '小红书' },
  { label: '小黑盒', value: '小黑盒' },
  { label: '通用', value: '通用' },
];

// ---------- RSS 源类型 ----------

export const SOURCE_TYPE_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '分组', value: 'GROUP' },
  { label: '指定源', value: 'SOURCE' },
  { label: '混合', value: 'MIXED' },
] as const;

// ---------- 时间预设 ----------

export const TIME_PRESETS = [
  { label: '1小时前', value: '1h' },
  { label: '6小时前', value: '6h' },
  { label: '截止上次任务结束', value: 'last_end' },
  { label: '自定义', value: 'custom' },
] as const;

// ---------- 分页默认值 ----------

export const DEFAULT_PAGE_SIZE = 20;

// ---------- 稿件编辑器 ----------

export const DRAFT_TEMPERATURE_MIN = 0;
export const DRAFT_TEMPERATURE_MAX = 2;
export const DRAFT_TEMPERATURE_STEP = 0.1;
export const DRAFT_TEMPERATURE_DEFAULT = 0.7;

// ---------- 布局 ----------

export const SIDER_WIDTH = 220;
export const HEADER_HEIGHT = 48;
