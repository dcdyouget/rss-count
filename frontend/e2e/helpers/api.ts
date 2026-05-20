// ============================================================
// Playwright Mock API 助手 — 通过 page.route() 拦截所有后端调用
// ============================================================

import type { Page, Route } from '@playwright/test';
import {
  MOCK_AI_DRAFT_CONTENT,
  MOCK_STRUCTURED_CONTENT,
  SSE_EVENTS_NORMAL,
  SSE_EVENTS_ERROR,
} from '../fixtures/test-data';

// ---------- Mock Database 状态 ----------

interface MockDatabase {
  rssSources: number;
  rssGroups: string[];
  tasks: Array<{
    id: number;
    name: string;
    status: string;
    reportId: number | null;
  }>;
  taskIdCounter: number;
  reportIdCounter: number;
  draftIdCounter: number;
  newsIdCounter: number;
}

const db: MockDatabase = {
  rssSources: 3,
  rssGroups: ['全部', '科技'],
  tasks: [],
  taskIdCounter: 1,
  reportIdCounter: 1,
  draftIdCounter: 1,
  newsIdCounter: 1,
};

// ---------- Route dispatcher ----------

async function handleRoute(route: Route): Promise<void> {
  const url = new URL(route.request().url());
  const path = url.pathname.replace(/^\/api\/v1/, '') || '/';
  const method = route.request().method();

  // SSE stream
  if (path.match(/^\/tasks\/\d+\/stream$/)) {
    return handleSSEStream(route, path);
  }

  // Export OPML
  if (path === '/rss-sources/export-opml') {
    return handleExportOpml(route);
  }

  // Dispatch by path segments
  const segments = path.split('/').filter(Boolean);

  try {
    await dispatchRoute(route, segments, method, url);
  } catch {
    await route.fulfill({ status: 500, body: 'Internal mock error' });
  }
}

async function dispatchRoute(
  route: Route,
  segments: string[],
  method: string,
  url: URL,
): Promise<void> {
  const [resource, id, action] = segments;

  switch (resource) {
    // ---------- RSS Sources ----------
    case 'rss-sources': {
      if (method === 'GET') {
        const groupId = url.searchParams.get('groupId');
        const allSources = generateRssSources();
        const filtered = groupId
          ? allSources.filter((s) => s.groupIds.includes(Number(groupId)))
          : allSources;
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(filtered) });
      }
      if (method === 'POST') {
        db.rssSources++;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: db.rssSources,
            url: 'https://example.com/new-feed',
            name: '新源',
            iconPath: null,
            createdAt: new Date().toISOString(),
            lastFetchAt: null,
            totalFetched: 0,
            isActive: true,
            groupIds: [1],
            groupNames: ['科技'],
          }),
        });
      }
      if (method === 'DELETE' && id) {
        db.rssSources = Math.max(0, db.rssSources - 1);
        return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      }
      if (id === 'import-opml' && method === 'POST') {
        const newCount = 10;
        db.rssSources += newCount;
        db.rssGroups.push('新分组');
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ created: newCount, skipped: 0, errors: [], total: newCount }),
        });
      }
      break;
    }

    // ---------- RSS Groups ----------
    case 'rss-groups': {
      if (method === 'GET') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(
            db.rssGroups.map((name, i) => ({
              id: i + 1,
              name,
              sourceCount: i === 0 ? db.rssSources : Math.floor(db.rssSources / 2),
              createdAt: new Date().toISOString(),
            })),
          ),
        });
      }
      if (method === 'PUT' && id) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: Number(id), name: '重命名分组', sourceCount: 5, createdAt: new Date().toISOString() }),
        });
      }
      if (method === 'POST') {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 99, name: '新建分组' }) });
      }
      break;
    }

    // ---------- Tasks ----------
    case 'tasks': {
      if (method === 'GET') {
        if (segments.length === 1 || (segments.length === 2 && !id)) {
          const page = Number(url.searchParams.get('page')) || 1;
          const size = Number(url.searchParams.get('size')) || 20;
          const items = db.tasks.map((t) => ({
            id: t.id,
            name: t.name,
            timeRangeStart: new Date().toISOString(),
            timeRangeEnd: new Date().toISOString(),
            status: t.status,
            sourceType: 'ALL',
            sourceConfig: { groupIds: [], sourceIds: [] },
            startedAt: new Date().toISOString(),
            endedAt: t.status === 'COMPLETED' ? new Date().toISOString() : undefined,
            reportId: t.reportId,
          }));
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ total: items.length, page, size, items }),
          });
        }
        if (id === 'suggest-name') {
          return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify('新任务') });
        }
        if (id === 'last-end-time') {
          return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ endedAt: null }) });
        }
        // Task detail
        if (id && segments.length === 2) {
          const task = db.tasks.find((t) => t.id === Number(id));
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              id: Number(id),
              name: task?.name ?? '测试任务',
              timeRangeStart: new Date().toISOString(),
              timeRangeEnd: new Date().toISOString(),
              status: task?.status ?? 'RUNNING',
              sourceType: 'ALL',
              sourceConfig: { groupIds: [], sourceIds: [] },
              startedAt: new Date().toISOString(),
              reportId: task?.reportId ?? null,
              sources: [
                { id: 1, name: '科技资讯', fetchedCount: 3 },
              ],
              news: generateNewsSummaries(),
            }),
          });
        }
      }
      if (method === 'POST' && segments.length === 1) {
        const taskId = db.taskIdCounter++;
        const reportId = db.reportIdCounter++;
        db.tasks.push({ id: taskId, name: '测试任务', status: 'RUNNING', reportId });
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ taskId, reportId }),
        });
      }
      break;
    }

    // ---------- Reports ----------
    case 'reports': {
      if (method === 'GET') {
        if (segments.length === 1) {
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              total: 1,
              page: 1,
              size: 20,
              items: [
                { id: 1, name: '测试报告', timeRangeStart: new Date().toISOString(), timeRangeEnd: new Date().toISOString(), newsCount: 3, createdAt: new Date().toISOString() },
              ],
            }),
          });
        }
        // Report detail
        if (id && segments.length === 2) {
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              id: Number(id),
              name: '测试报告',
              timeRangeStart: new Date().toISOString(),
              timeRangeEnd: new Date().toISOString(),
              newsCount: 3,
              createdAt: new Date().toISOString(),
              news: generateNewsSummaries(),
            }),
          });
        }
        // Report news detail
        if (id && action === 'news' && segments.length === 4) {
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(generateNewsDetail(Number(segments[3]))),
          });
        }
      }
      break;
    }

    // ---------- News ----------
    case 'news': {
      if (method === 'GET') {
        if (segments.length === 1) {
          const keyword = url.searchParams.get('keyword');
          let items = generateNewsItems();
          if (keyword) {
            items = items.filter((n) => n.title.includes(keyword));
          }
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ total: items.length, page: 1, size: 20, items }),
          });
        }
        if (id === 'material-pile') {
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              total: 3,
              items: [
                { id: 1, title: 'AI 突破：新模型超越人类表现', materialPileAddedAt: new Date().toISOString() },
                { id: 2, title: '量子计算商业化加速', materialPileAddedAt: new Date().toISOString() },
              ],
            }),
          });
        }
        if (id === 'batch-material-pile') {
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ affected: 3 }),
          });
        }
        // News detail
        if (id && segments.length === 2) {
          return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(generateNewsDetail(Number(id))),
          });
        }
      }
      if (method === 'PUT' && id && segments.length === 3 && action === 'read') {
        return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      }
      if (method === 'POST' && id === 'batch-material-pile') {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ affected: 3 }) });
      }
      break;
    }

    // ---------- Drafts ----------
    case 'drafts': {
      if (method === 'GET' && segments.length === 1) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            total: 1,
            items: [
              { id: 1, name: '测试稿件', style: 'news', targetPlatform: 'wechat', newsCount: 3, latestVersion: 1, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
            ],
          }),
        });
      }
      if (method === 'POST' && segments.length === 1) {
        const draftId = db.draftIdCounter++;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: draftId,
            name: '测试稿件',
            prompt: '',
            temperature: 0.7,
            style: 'news',
            targetPlatform: 'wechat',
            latestVersion: 1,
            latestContent: '',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            news: generateNewsSummaries(),
          }),
        });
      }
      if (method === 'GET' && id) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: Number(id),
            name: '测试稿件',
            prompt: '测试提示词',
            temperature: 0.7,
            style: 'news',
            targetPlatform: 'wechat',
            latestVersion: 1,
            latestContent: MOCK_AI_DRAFT_CONTENT,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            news: generateNewsSummaries(),
          }),
        });
      }
      if (method === 'PUT' && id) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: Number(id),
            name: '测试稿件',
            prompt: '',
            temperature: 0.7,
            style: 'news',
            targetPlatform: 'wechat',
            latestVersion: 2,
            latestContent: MOCK_AI_DRAFT_CONTENT,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            news: generateNewsSummaries(),
          }),
        });
      }
      if (method === 'DELETE' && id) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      }
      if (method === 'POST' && id && action === 'generate') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: MOCK_AI_DRAFT_CONTENT, version: 2 }),
        });
      }
      break;
    }

    // ---------- Dashboard ----------
    case 'dashboard': {
      if (segments[1] === 'stats') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            taskCount: { today: db.tasks.length, yesterday: 0, change: db.tasks.length, changePercent: null },
            reportCount: { today: db.tasks.filter((t) => t.status === 'COMPLETED').length, yesterday: 0, change: 0, changePercent: null },
            newsCount: { today: 15, yesterday: 10, change: 5, changePercent: 50 },
          }),
        });
      }
      if (segments[1] === 'recent-tasks') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(
            db.tasks.map((t) => ({
              id: t.id,
              name: t.name,
              timeRangeStart: new Date().toISOString(),
              timeRangeEnd: new Date().toISOString(),
              status: t.status,
              startedAt: new Date().toISOString(),
              endedAt: t.status === 'COMPLETED' ? new Date().toISOString() : undefined,
              reportId: t.reportId,
            })),
          ),
        });
      }
      if (segments[1] === 'recent-reports') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 1, name: '测试报告', newsCount: 3, createdAt: new Date().toISOString() },
          ]),
        });
      }
      break;
    }

    // ---------- Settings ----------
    case 'settings': {
      if (method === 'GET') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            taskIntervalHours: 24,
            aiApiUrl: 'https://api.example.com/ai',
            aiApiKey: 'sk-***',
            aiModel: 'gpt-4',
            defaultGroupId: null,
            updatedAt: new Date().toISOString(),
          }),
        });
      }
      if (method === 'PUT') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            taskIntervalHours: 12,
            aiApiUrl: 'https://api.example.com/ai',
            aiApiKey: 'sk-***',
            aiModel: 'gpt-4',
            defaultGroupId: 1,
            updatedAt: new Date().toISOString(),
          }),
        });
      }
      break;
    }
  }

  // Fallback — 404
  await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Not found' }) });
}

// ---------- SSE ----------

async function handleSSEStream(route: Route, path: string): Promise<void> {
  const events = SSE_EVENTS_NORMAL;
  const body = events.join('\n') + '\n';

  await route.fulfill({
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
    body,
  });
}

// ---------- OPML Export ----------

async function handleExportOpml(route: Route): Promise<void> {
  const opml = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>RSS Count 订阅源</title></head>
  <body>
    <outline text="科技" title="科技">
      <outline type="rss" text="源1" xmlUrl="https://example.com/1"/>
      <outline type="rss" text="源2" xmlUrl="https://example.com/2"/>
    </outline>
  </body>
</opml>`;

  await route.fulfill({
    status: 200,
    headers: { 'Content-Type': 'application/xml', 'Content-Disposition': 'attachment; filename="rss-sources.opml"' },
    body: opml,
  });
}

// ---------- Data Generators ----------

function generateRssSources() {
  const sources = [
    { id: 1, url: 'https://example.com/tech', name: '科技资讯', iconPath: null, createdAt: new Date().toISOString(), lastFetchAt: new Date().toISOString(), totalFetched: 150, isActive: true, groupIds: [1], groupNames: ['科技'] },
    { id: 2, url: 'https://example.com/news', name: '综合新闻', iconPath: null, createdAt: new Date().toISOString(), lastFetchAt: null, totalFetched: 0, isActive: true, groupIds: [1], groupNames: ['科技'] },
    { id: 3, url: 'https://example.com/design', name: '设计博客', iconPath: null, createdAt: new Date().toISOString(), lastFetchAt: new Date().toISOString(), totalFetched: 42, isActive: true, groupIds: [2], groupNames: ['设计'] },
  ];
  while (sources.length < db.rssSources) {
    const n = sources.length + 1;
    sources.push({ id: n, url: `https://example.com/source${n}`, name: `源${n}`, iconPath: null, createdAt: new Date().toISOString(), lastFetchAt: null, totalFetched: 0, isActive: true, groupIds: [1], groupNames: ['科技'] });
  }
  return sources;
}

function generateNewsSummaries() {
  return [
    { id: 1, title: 'AI 突破：新模型超越人类表现', summary: '最新 AI 模型在多项基准测试中超越人类水平。', headerImageUrl: null, sourceRssName: '科技资讯', publishedAt: new Date().toISOString() },
    { id: 2, title: '量子计算商业化加速', summary: '多家科技巨头宣布量子计算商用计划。', headerImageUrl: null, sourceRssName: '科技资讯', publishedAt: new Date().toISOString() },
    { id: 3, title: '开源社区迎来新协作平台', summary: '全新开源协作平台发布。', headerImageUrl: null, sourceRssName: '科技资讯', publishedAt: new Date().toISOString() },
  ];
}

function generateNewsItems() {
  return [
    { id: 1, title: 'AI 突破：新模型超越人类表现', summary: '最新 AI 模型在多项基准测试中超越人类水平。', headerImageUrl: null, sourceRssName: '科技资讯', publishedAt: new Date().toISOString(), reportName: '测试报告', reportId: 1, isRead: false, inMaterialPile: false },
    { id: 2, title: '量子计算商业化加速', summary: '多家科技巨头宣布量子计算商用计划。', headerImageUrl: null, sourceRssName: '科技资讯', publishedAt: new Date().toISOString(), reportName: '测试报告', reportId: 1, isRead: false, inMaterialPile: false },
  ];
}

function generateNewsDetail(id: number) {
  return {
    id,
    title: 'AI 突破：新模型超越人类表现',
    summary: '最新 AI 模型在多项基准测试中超越人类水平。',
    headerImageUrl: null,
    sourceRssName: '科技资讯',
    publishedAt: new Date().toISOString(),
    reportName: '测试报告',
    reportId: 1,
    isRead: false,
    inMaterialPile: false,
    author: 'AI 研究团队',
    sourceUrl: 'https://example.com/tech/ai-breakthrough',
    tags: ['AI', '机器学习', '科技前沿'],
    structuredContent: MOCK_STRUCTURED_CONTENT,
    materialPileAddedAt: undefined,
  };
}

// ---------- Public Helpers ----------

/**
 * 重置 Mock 数据库状态
 */
export function resetDatabase(): void {
  db.rssSources = 3;
  db.rssGroups = ['全部', '科技'];
  db.tasks = [];
  db.taskIdCounter = 1;
  db.reportIdCounter = 1;
  db.draftIdCounter = 1;
  db.newsIdCounter = 1;
}

/**
 * 注入初始 RSS 源种子数据（创建 1 个已完成任务及相关报告）
 */
export function seedRssSources(): void {
  db.tasks.push({ id: 0, name: '历史任务', status: 'COMPLETED', reportId: 0 });
}

/**
 * 返回当前任务数量（供测试断言使用）
 */
export function getTaskCount(): number {
  return db.tasks.length;
}

/**
 * 设置 Mock API 路由拦截（在 test.beforeEach 中调用）
 */
export async function setupApiMocks(page: Page): Promise<void> {
  await page.route('**/api/v1/**', handleRoute);
}
