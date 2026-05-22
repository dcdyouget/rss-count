# RSS Count 架构文档

## 1. 项目概述

RSS Count 是一个基于 RSS 的 AI 新闻聚合与内容生产平台。核心功能包括 RSS 源管理、定时拉取新闻、AI 格式化（标题清洗、HTML 正文提取、AI 摘要/标签、SimHash 去重）、报告生成，以及基于素材堆的 AI 稿件生成与发布。

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    用户浏览器 (React SPA)                │
│  React 18 + TypeScript + Ant Design 5 + TanStack Query │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP/SSE
                         ▼
┌─────────────────────────────────────────────────────────┐
│                 Quarkus 后端 (Java 21)                    │
│  Controller(8) → Service(10) → Entity/Panache(12+3)    │
│  + TaskExecutor + TaskScheduler + SSE                   │
│                         │                               │
│                    SQLite (DELETE mode)                 │
│                    rsscount.db                          │
│                         │                               │
│               AI API (OpenAI-compatible)                │
└─────────────────────────────────────────────────────────┘
```

## 3. 技术栈

| 层次 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 21 | Virtual Threads |
| 框架 | Quarkus | 3.15.1 | REST + ORM + SSE + Scheduler |
| ORM | Hibernate + Panache | 内置 | Active Record |
| 数据库 | SQLite | 3.x | 单文件, DELETE journal |
| RSS | ROME | 2.1.0 | RSS/Atom 解析 |
| HTML | JSoup | 1.18.3 | 正文提取+清洗+图片本地化 |
| 分词 | jieba-analysis | 1.0.2 | SimHash 分词 |
| 前端 | React 18 + Ant Design 5 + TanStack Query + Zustand | - | SPA |
| 部署 | Docker + docker-compose | - | eclipse-temurin:21-jre |

## 4. 数据模型

12 个实体 + 3 个关联表，IDENTITY ID 策略：
- RssGroup ⟷ RssSource (N:M via RssSourceGroup)
- Task → Report (1:1)
- Report → News (1:N)
- News ⟷ Tag (N:M via NewsTag)
- Draft → DraftVersion (1:N)
- Draft ⟷ News (N:M via DraftNews)
- Settings (单行, id=1)

## 5. API (33 端点)

| 模块 | 端点数 | 说明 |
|------|--------|------|
| Dashboard | 3 | stats, recent-tasks, recent-reports |
| Tasks | 6 | CRUD + SSE stream + suggest-name + last-end-time |
| Reports | 3 | list, detail, news detail |
| News | 5 | list, detail, mark-read, batch-material-pile, material-pile |
| Drafts | 6 | CRUD + generate |
| RssSources | 5 | list, add, delete, import-opml, export-opml |
| RssGroups | 4 | CRUD |
| Settings | 2 | get, update |
| Health | 1 | health check |

## 6. 核心流程

```
RssFetchService(ROME) → TaskExecutor(去重+过滤) 
  → NewsFormatService(8步管道) → SQLite → 前端展示
```

详见 `BUSINESS_FLOW.md`。

## 7. 页面路由

| 路由 | 页面 |
|------|------|
| / | Dashboard |
| /tasks | TaskManagement |
| /tasks/:id | TaskDetail (SSE) |
| /reports | ReportManagement |
| /reports/:id | ReportDetail |
| /news | NewsManagement |
| /drafts | DraftManagement |
| /rss-sources | RssSourceManagement |
| /settings | Settings |
