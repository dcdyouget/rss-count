// ============================================================
// 后端 DTO 类型定义（对应 /api/v1 所有接口的请求/响应）
// ============================================================

// ---------- 通用 ----------

export interface PaginatedResponse<T> {
  total: number;
  page: number;
  size: number;
  items: T[];
}

// ---------- RSS 分组 ----------

export interface RssGroup {
  id: number;
  name: string;
  sourceCount: number;
  createdAt: string;
}

// ---------- RSS 源 ----------

export interface RssSource {
  id: number;
  url: string;
  name: string;
  iconPath: string | null;
  createdAt: string;
  lastFetchAt: string | null;
  totalFetched: number;
  isActive: boolean;
  groupIds: number[];
  groupNames: string[];
}

// ---------- 任务 ----------

export type TaskStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export type SourceType = 'ALL' | 'GROUP' | 'SOURCE' | 'MIXED';

export interface SourceConfig {
  groupIds: number[];
  sourceIds: number[];
}

export interface Task {
  id: number;
  name: string;
  timeRangeStart: string;
  timeRangeEnd: string;
  status: TaskStatus;
  sourceType: SourceType;
  sourceConfig: SourceConfig;
  startedAt: string;
  endedAt?: string;
  reportId?: number | null;
  errorMessage?: string;
}

export interface TaskWithDetails extends Task {
  sources?: TaskSource[];
  news?: NewsItem[];
}

export interface TaskSource {
  id: number;
  name: string;
  fetchedCount: number;
}

export interface CreateTaskRequest {
  name: string;
  timeRangeStart: string;
  timeRangeEnd: string;
  sourceType: SourceType;
  sourceConfig?: SourceConfig;
}

export interface TaskResponse {
  taskId: number;
  reportId: number;
}

export interface TaskListParams {
  page?: number;
  size?: number;
  status?: TaskStatus;
  createdAfter?: string;
  createdBefore?: string;
}

export interface SuggestNameResponse {
  name: string;
}

export interface LastEndTimeResponse {
  endedAt: string | null;
}

// ---------- 报告 ----------

export interface Report {
  id: number;
  name: string;
  timeRangeStart?: string;
  timeRangeEnd?: string;
  newsCount: number;
  createdAt: string;
}

export interface ReportWithNews extends Report {
  timeRangeStart: string;
  timeRangeEnd: string;
  news: NewsSummary[];
}

export interface ReportListParams {
  page?: number;
  size?: number;
}

// ---------- 新闻 ----------

export interface NewsSummary {
  id: number;
  title: string;
  summary: string;
  headerImageHtml: string | null;
  sourceRssName: string;
  publishedAt: string;
}

export interface NewsItem extends NewsSummary {
  reportName?: string;
  reportId?: number;
  isRead: boolean;
  inMaterialPile: boolean;
}

export interface NewsDetail extends NewsItem {
  author?: string;
  sourceUrl?: string;
  tags?: string[];
  structuredContent?: string;
  materialPileAddedAt?: string;
}

export interface NewsListParams {
  page?: number;
  size?: number;
  keyword?: string;
  reportName?: string;
  isRead?: boolean;
}

export interface BatchMaterialPileRequest {
  newsIds: number[];
  action: 'ADD' | 'REMOVE';
}

export interface BatchMaterialPileResponse {
  affected: number;
}

// ---------- 稿件 ----------

export interface DraftStyle {
  label: string;
  value: string;
}

export interface DraftPlatform {
  label: string;
  value: string;
}

export interface Draft {
  id: number;
  name: string;
  prompt: string;
  temperature: number;
  style: string;
  targetPlatform: string;
  latestVersion: number;
  latestContent?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DraftListSummary {
  id: number;
  name: string;
  style: string;
  targetPlatform: string;
  newsCount: number;
  latestVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface DraftWithNews extends Draft {
  news: NewsSummary[];
}

export interface DraftVersion {
  version: number;
  content: string;
  prompt: string;
  temperature: number;
  style: string;
  targetPlatform: string;
  createdAt: string;
}

export interface CreateDraftRequest {
  name: string;
  newsIds: number[];
  prompt: string;
  temperature: number;
  style: string;
  targetPlatform: string;
}

export interface UpdateDraftRequest {
  name: string;
  newsIds: number[];
  prompt: string;
  temperature: number;
  style: string;
  targetPlatform: string;
}

export interface GenerateResponse {
  content: string;
  version: number;
}

export interface DraftListParams {
  page?: number;
  size?: number;
}

export interface MaterialPileItem {
  id: number;
  title: string;
  materialPileAddedAt: string;
}

// ---------- 仪表盘 ----------

export interface StatItem {
  today: number;
  yesterday: number;
  change: number;
  changePercent: number | null;
}

export interface DashboardStats {
  taskCount: StatItem;
  reportCount: StatItem;
  newsCount: StatItem;
}

export interface RecentTask {
  id: number;
  name: string;
  timeRangeStart: string;
  timeRangeEnd: string;
  status: TaskStatus;
  startedAt: string;
  endedAt?: string;
  reportId: number | null;
}

export interface RecentReport {
  id: number;
  name: string;
  newsCount: number;
  createdAt: string;
}

// ---------- 设置 ----------

export interface Settings {
  taskIntervalHours: number;
  aiApiUrl: string;
  aiApiKey: string;
  aiModel: string;
  defaultGroupId: number | null;
  updatedAt: string;
}

export interface UpdateSettingsRequest {
  taskIntervalHours: number;
  aiApiUrl: string;
  aiApiKey: string;
  aiModel: string;
  defaultGroupId: number | null;
}

// ---------- RSS 导入/导出 ----------

export interface OpmlImportResponse {
  created: number;
  skipped: number;
  errors: string[];
  total: number;
}

// ---------- SSE ----------

export interface SSEProgress {
  pulling: {
    done: boolean;
    currentSource?: string;
    sourceProgress?: string;
    totalFetched?: number;
  };
  formatting: {
    done: boolean;
    formatted?: number;
    total?: number;
    currentAction?: string;
  };
}

export interface SSEComplete {
  reportId: number;
}

export interface SSEError {
  message: string;
}

export type SSEEvent =
  | { type: 'progress'; data: SSEProgress }
  | { type: 'complete'; data: SSEComplete }
  | { type: 'error'; data: SSEError };
