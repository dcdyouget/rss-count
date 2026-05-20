# RSS Count — AI 新闻聚合与内容生产平台

基于 RSS 的新闻自动拉取、AI 格式化、素材管理与 AI 稿件生成工具。

## 技术栈

- **后端**：Java 21 + Quarkus 3.x + GraalVM Native Image + SQLite
- **前端**：React 18 + Vite + TypeScript + Ant Design 5 + TipTap

## 项目结构

```
项目文档/
├── design.txt              # 原始设计需求
├── 后端设计/                # 后端设计文档（7 篇）
│   ├── 任务清单.md           # 可分发给 agent 的开发任务
│   ├── 技术选型.md
│   ├── 数据库设计.md
│   ├── API设计.md
│   ├── 业务流程.md
│   ├── 构建与部署.md
│   └── 测试设计.md
├── 前端设计/                # 前端设计文档（7 篇）
│   ├── 任务清单.md
│   ├── 技术选型.md
│   ├── 设计规范.md
│   ├── 路由与组件树.md
│   ├── 页面设计.md
│   ├── 状态管理与数据流.md
│   └── 测试设计.md
├── 测试文档/                # 跨端测试文档（2 篇）
│   ├── 测试策略总览.md
│   └── E2E测试用例.md
└── 原型图/                  # 5 张 UI 原型图
```

## 快速开始

```bash
# 开发
cd backend && ./mvnw quarkus:dev     # 后端 :8080
cd frontend && npm run dev           # 前端 :5173

# 部署
docker build -t rss-count .
docker compose up -d
```
