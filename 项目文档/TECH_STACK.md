# RSS Count 技术栈文档

## 后端

### Java 21
- Virtual Threads：每源一线程零开销并发拉取 RSS
- `Thread.startVirtualThread()` / `Executors.newVirtualThreadPerTaskExecutor()`

### Quarkus 3.15.1
- RESTEasy Reactive + Jackson：REST API
- Hibernate + Panache：Active Record ORM
- Scheduler：`@Scheduled` 定时任务
- REST Client：OpenAI-compatible API 调用
- SSE：实时进度推送

### SQLite (DELETE journal mode)
- 单文件、零运维
- `busy_timeout=60000`：写锁等待
- `max-size=2`：最大 2 连接（1 主 + 1 format）
- IDENTITY ID 策略：无序列表，避免行锁竞争
- `hibernate-community-dialects` 的 SQLiteDialect

### ROME 2.1.0
- RSS 0.9/1.0/2.0 + Atom 解析
- `SyndFeedInput.build()` → `SyndFeed.getEntries()`

### JSoup 1.18.3
- HTML 解析：`Jsoup.parse(html)`
- 安全清洗：`Safelist.relaxed()` + 自定义规则
- 噪声移除：script/style/iframe/.ad/.sidebar 等 15 个选择器
- 图片提取：`doc.select("img")` → 首图作头图

### jieba-analysis 1.0.2
- 中文分词：`SEGMENTER.process(text, SegMode.INDEX)`
- 作为 SimHash 的 TF 特征输入

### ContentExtractor（自实现）
- `clean(html, baseUri)`：HTML 安全清洗 + 图片本地化
- `extractHeaderImage(html)`：首图提取

### ImageService（自实现）
- 远程图片下载到 `/app/data/img/{uuid}.{ext}`
- 10s 超时，10MB 上限
- 失败降级保留原 URL

### SimHash（自实现）
- jieba 分词 + 字符 bigram → TF 加权 → 64 位指纹
- 汉明距离 ≤ 3 判定重复

## 前端

### React 18 + TypeScript 5.5
- SPA，函数组件 + Hooks

### Vite 5.4
- 开发秒级启动，HMR 毫秒级

### Ant Design 5.21
- ConfigProvider 全局主题（primary=#2563EB, borderRadius=8）
- Table/Form/Drawer/Steps/Select/DatePicker 等组件

### TanStack Query 5.56
- 服务端状态：每个 API 端点对应 useQuery/useMutation
- staleTime=30s, retry=1

### Zustand 4.5
- 客户端状态：draftStore, uiStore

### React Router 6.26
- createBrowserRouter, 11 条路由

### Axios 1.7
- HTTP 客户端，拦截器统一错误处理

### Framer Motion 11
- 报告详情页卡片↔详情过渡动画

### dayjs
- UTC+8 时区格式化：`dayjs.tz(isoStr, 'Asia/Shanghai')`

### TipTap 2.8
- 富文本编辑器（稿件的设计依赖，实际当前用 TextArea）

## 部署

### Docker
- 基础镜像：eclipse-temurin:21-jre (~180MB)
- 单容器：JAR 内嵌前端静态资源 + REST API
- 数据卷：`/root/data:/app/data`

### docker-compose
- 端口 8080
- restart: unless-stopped

## 选型理由

| 选择 | 理由 |
|------|------|
| Java 21 | Virtual Threads 并发拉 RSS |
| Quarkus | 启动快、内置 SSE/Panache/Scheduler |
| SQLite | 零运维、单文件、适合单机 |
| ROME | 最成熟的 Java RSS 解析器 |
| JSoup | API 最简洁的 Java HTML 解析器 |
| IDENTITY ID | 无序列表 → 无行锁竞争 |
| DELETE journal | busy_timeout 可处理 SQLITE_BUSY |
| FORMAT_THREADS=1 | 串行写避免 SQLite 写冲突 |
| React + Ant Design | 企业级 UI，组件丰富 |
| TanStack Query | 缓存/去重/后台刷新 |
