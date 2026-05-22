# RSS Count 业务流程文档

## 1. 整体流程

```
用户创建任务 → 解析RSS源列表 → 拉取新闻(时间过滤+SimHash去重) 
  → 提交格式化 → AI格式化(8步管道) → 生成报告 → 前端展示
```

## 2. RSS 拉取与去重

### 2.1 RSS 拉取（RssFetchService.parseFeed）

```
HttpURLConnection → ETag/If-Modified-Since 缓存检查
  ├─ 304 Not Modified → 跳过该源
  └─ 200 OK → ROME SyndFeedInput 解析 → SyndFeed.getEntries()
```

配置：connectTimeout=15s, readTimeout=30s

### 2.2 去重（内存 SimHash，仅当前任务）

每拉取一条新闻：
1. **时间过滤**：publishedAt 是否在 task.timeRangeStart~End 内
2. **SimHash 去重**：ConcurrentHashMap 分组，汉明距离 ≤ 3 判定重复
   - SimHash = jieba 分词 + TF 加权 → 64 位指纹

### 2.3 ETag 缓存

RssSource.etag / lastModified 存储 HTTP 缓存头，下次请求带 If-None-Match / If-Modified-Since

## 3. 新闻格式化管道（8 步）

`NewsFormatService.formatOneNews()` 每步顺序执行：

| 步骤 | 操作 | 说明 |
|------|------|------|
| 0 | persist | 先获取 ID |
| 1 | 标题清洗 | TextCleaner：去HTML/Emoji/前缀/全角转半角 |
| 2 | HTML清洗+图片本地化 | JSoup Safelist.relaxed() → ImageService.saveImg() |
| 3 | 头图提取 | 首张有效图片 → imageService.saveImg() |
| 4 | 时间规范化 | ROME 自动处理（RssFetchService阶段） |
| 5 | AI 摘要 | plainText[:1000] → OpenAI API → 200字摘要 |
| 6 | AI 标签 | plainText[:2000] → OpenAI API → 匹配标签库 |
| 7 | SimHash | 兜底重算 |
| 8 | persist | 持久化格式化后字段 |

## 4. 任务生命周期

```
CREATE → RUNNING → 拉取阶段(遍历源) → 格式化阶段(单线程池)
  ├─ 全部完成 → COMPLETED
  └─ 全部失败 → FAILED
```

- FORMAT_THREADS=1：SQLite 串行写
- Virtual Threads：并发拉取多源
- SSE 实时推送进度

## 5. SSE 进度推送

端点：`GET /api/v1/tasks/{id}/stream`

三种事件：
- `progress`: {pulling: {done, currentSource, sourceProgress, totalFetched}, formatting: {done, formatted, total, currentAction}}
- `complete`: {reportId}
- `error`: {message}

前端 useSSE hook 支持断线重连（指数退避 2s/4s/8s，最多 3 次）

## 6. 稿件生成流程

```
DraftService.generate(id)
  → 加载 Draft + 关联 News + 提取 plainText
  → 构建 AI prompt (system + user + 素材)
  → AiService.generateDraft() → DraftVersion 写入
```

## 7. 定时任务

`@Scheduled(every = "1h")` → TaskScheduler：
- 检查 taskIntervalHours > 0
- 检查无 RUNNING 任务
- 检查有活跃 RSS 源
- 确定时间范围（上次完成时间 ~ 当前）

## 8. 启动恢复

`@PostConstruct resumeRunningTasks()` → 扫描 RUNNING 任务 → 重新执行

## 9. 错误处理

| 场景 | 处理 |
|------|------|
| 单源超时/解析失败 | 跳过，继续下个源 |
| 单条解析失败 | 跳过该条目 |
| AI 调用失败 | 降级，留空 |
| 全部源失败 | 任务 → FAILED |
