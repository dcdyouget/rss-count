# API 设计

## 约定

- 基础路径：`/api/v1`
- 请求/响应格式：JSON
- 时间格式：ISO 8601，带时区偏移 `2026-05-20T08:00:00+08:00`
- 分页：查询参数 `page`（从 1 开始）、`size`（默认 20）
- 分页响应：`{ "total": N, "page": N, "size": N, "items": [...] }`

---

## 一、仪表盘

### 1. GET /dashboard/stats

返回今日任务数/报告数/新闻数及昨日同比。

**响应**：
```json
{
  "taskCount":   {"today":5, "yesterday":3,  "change":2,  "changePercent":66.7},
  "reportCount": {"today":4, "yesterday":2,  "change":2,  "changePercent":100},
  "newsCount":   {"today":156,"yesterday":89, "change":67, "changePercent":75.3}
}
```
- 昨天为 0 → `changePercent` 为 `null`

### 2. GET /dashboard/recent-tasks

最近 5 条任务。

**响应**：
```json
[
  {
    "id": 15,
    "name": "2026年5月20日-第3次任务",
    "timeRangeStart": "2026-05-20T08:00:00+08:00",
    "timeRangeEnd": "2026-05-20T14:00:00+08:00",
    "status": "COMPLETED",
    "startedAt": "2026-05-20T08:00:05+08:00",
    "endedAt": "2026-05-20T08:05:30+08:00",
    "reportId": 15
  }
]
```
- COMPLETED 附带 `reportId`，前端据此生成跳转链接
- RUNNING/FAILED 时 `reportId` 为 `null`

### 3. GET /dashboard/recent-reports

最近 3 条报告。

**响应**：
```json
[
  {
    "id": 15,
    "name": "2026年5月20日-第3次报告",
    "newsCount": 42,
    "createdAt": "2026-05-20T08:05:30+08:00"
  }
]
```

---

## 二、任务管理

### 4. POST /tasks

创建任务，异步开始执行。

**请求**：
```json
{
  "name": "2026年5月20日-第4次任务",
  "timeRangeStart": "2026-05-20T08:00:00+08:00",
  "timeRangeEnd": "2026-05-20T14:00:00+08:00",
  "sourceType": "GROUP",
  "sourceConfig": {"groupIds": [1,2], "sourceIds": []}
}
```
- `name`：必填，前端传入
- `sourceType`：ALL / GROUP / SOURCE / MIXED
- `sourceConfig`：与 sourceType 对应；ALL 时忽略此字段
- 校验：`timeRangeStart < timeRangeEnd`，`sourceType` 与 `sourceConfig` 一致

**响应 201**：
```json
{ "taskId": 16, "reportId": 16 }
```

**错误**：
- 400 — 参数校验失败
- 422 — 时间范围无效

### 5. GET /tasks

分页查询任务列表。

**查询参数**：`?page=1&size=20&status=COMPLETED&createdAfter=...&createdBefore=...`

**响应**：
```json
{
  "total": 50,
  "page": 1,
  "size": 20,
  "items": [
    {
      "id": 15,
      "name": "2026年5月20日-第3次任务",
      "timeRangeStart": "2026-05-20T08:00:00+08:00",
      "timeRangeEnd": "2026-05-20T14:00:00+08:00",
      "status": "COMPLETED",
      "startedAt": "2026-05-20T08:00:05+08:00",
      "endedAt": "2026-05-20T08:05:30+08:00",
      "reportId": 15
    }
  ]
}
```

### 6. GET /tasks/{id}

任务详情。

**RUNNING 任务响应**：
```json
{
  "id": 16,
  "name": "...",
  "timeRangeStart": "...",
  "timeRangeEnd": "...",
  "status": "RUNNING",
  "sourceType": "GROUP",
  "sourceConfig": {"groupIds":[1]},
  "startedAt": "..."
}
```

**COMPLETED 任务响应**：
```json
{
  "id": 15,
  "name": "...",
  "timeRangeStart": "...",
  "timeRangeEnd": "...",
  "status": "COMPLETED",
  "startedAt": "...",
  "endedAt": "...",
  "reportId": 15,
  "sourceType": "GROUP",
  "sourceConfig": {"groupIds":[1]},
  "sources": [
    {"id":1, "name":"36氪", "fetchedCount":10}
  ],
  "news": [
    {"id":101, "title":"...", "sourceRssName":"36氪"},
    "..."
  ]
}
```
- `news` 分页：`?newsPage=1&newsSize=20`

**FAILED 任务响应**：同上 + `"errorMessage": "..."`

### 7. GET /tasks/{id}/stream

SSE 进度推送。

**事件类型**：

```
event: progress
data: {"pulling":{"done":false,"currentSource":"36氪","sourceProgress":"3/15","totalFetched":42},"formatting":{"done":false,"formatted":8,"total":42,"currentAction":"正在生成概览"}}

event: complete
data: {"reportId":15}

event: error
data: {"message":"全部RSS源拉取失败"}
```

### 8. GET /tasks/suggest-name

返回建议任务名称。**纯文本响应**。

```
GET /tasks/suggest-name
→ "2026年5月20日-第4次任务"
```

### 9. GET /tasks/last-end-time

返回上次 COMPLETED 任务的结束时间。

**响应**：
```json
{ "endedAt": "2026-05-20T08:05:30+08:00" }
```
无历史任务时返回 `null`。

---

## 三、报告管理

### 10. GET /reports

分页查询报告列表。

**查询参数**：`?page=1&size=20`

**响应**：
```json
{
  "total": 30,
  "page": 1,
  "size": 20,
  "items": [
    {
      "id": 15,
      "name": "2026年5月20日-第3次报告",
      "timeRangeStart": "2026-05-20T08:00:00+08:00",
      "timeRangeEnd": "2026-05-20T14:00:00+08:00",
      "newsCount": 42,
      "createdAt": "2026-05-20T08:05:30+08:00"
    }
  ]
}
```

### 11. GET /reports/{id}

报告详情，含全量新闻列表（前端处理滚动和虚拟列表）。

**响应**：
```json
{
  "id": 15,
  "name": "2026年5月20日-第3次报告",
  "timeRangeStart": "2026-05-20T08:00:00+08:00",
  "timeRangeEnd": "2026-05-20T14:00:00+08:00",
  "newsCount": 42,
  "createdAt": "2026-05-20T08:05:30+08:00",
  "news": [
    {
      "id": 101,
      "title": "AI 行业迎来新突破",
      "summary": "近日，多家科技公司发布了...",
      "headerImageUrl": "/static/images/news101_header.jpg",
      "sourceRssName": "36氪",
      "publishedAt": "2026-05-20T10:30:00+08:00"
    }
  ]
}
```

### 12. GET /reports/{id}/news/{newsId}

报告内新闻详情（卡片展开 → 新闻内容界面）。

**响应**：
```json
{
  "id": 101,
  "title": "AI 行业迎来新突破",
  "author": "张三",
  "sourceRssName": "36氪",
  "sourceUrl": "https://36kr.com/p/12345",
  "publishedAt": "2026-05-20T10:30:00+08:00",
  "tags": ["AI", "科技", "融资"],
  "isRead": false,
  "inMaterialPile": false,
  "structuredContent": [
    {"type":"heading","level":2,"text":"..."},
    {"type":"paragraph","text":"..."},
    {"type":"image","src":"/static/images/news101_img1.jpg","alt":"..."}
  ]
}
```

---

## 四、新闻管理

### 13. GET /news

分页查询新闻列表。

**查询参数**：`?page=1&size=20&keyword=AI&reportName=2026年5月&isRead=false`

- `keyword`：标题模糊搜索
- `reportName`：报告名模糊筛选报告范围
- `isRead`：可选，`true/false`

**响应**：
```json
{
  "total": 150,
  "page": 1,
  "size": 20,
  "items": [
    {
      "id": 101,
      "title": "AI 行业迎来新突破",
      "summary": "近日...",
      "headerImageUrl": "/static/images/news101_header.jpg",
      "sourceRssName": "36氪",
      "reportName": "2026年5月20日-第3次报告",
      "reportId": 15,
      "isRead": false,
      "inMaterialPile": false,
      "publishedAt": "2026-05-20T10:30:00+08:00"
    }
  ]
}
```

### 14. GET /news/{id}

新闻详情（与 #12 结构相同，不限 reportId 范围）。

### 15. POST /news/batch-material-pile

批量加入/移出素材堆。

**请求**：
```json
{
  "newsIds": [1, 2, 3],
  "action": "ADD"
}
```
- `action`：ADD / REMOVE
- 幂等：重复 ADD 不报错

**响应**：
```json
{ "affected": 3 }
```

### 16. GET /news/material-pile

素材堆列表。

**查询参数**：`?page=1&size=50`

**响应**：
```json
{
  "total": 12,
  "items": [
    {
      "id": 101,
      "title": "AI 行业迎来新突破",
      "materialPileAddedAt": "2026-05-20T14:30:00+08:00"
    }
  ]
}
```

---

## 五、稿件管理

### 17. POST /drafts

创建稿件。

**请求**：
```json
{
  "name": "AI行业周报",
  "newsIds": [1, 5, 12],
  "prompt": "请综合这些新闻，写一篇800字的行业分析",
  "temperature": 0.7,
  "style": "正式",
  "targetPlatform": "知乎"
}
```
- `newsIds`：非空
- `temperature`：0.0 ~ 2.0

**响应 201**：返回完整 Draft 对象。

### 18. GET /drafts

分页查询稿件列表。

**查询参数**：`?page=1&size=20`

**响应**：
```json
{
  "total": 8,
  "items": [
    {
      "id": 1,
      "name": "AI行业周报",
      "style": "正式",
      "targetPlatform": "知乎",
      "newsCount": 3,
      "latestVersion": 2,
      "createdAt": "...",
      "updatedAt": "..."
    }
  ]
}
```

### 19. GET /drafts/{id}

稿件详情，含关联新闻列表。

**响应**：
```json
{
  "id": 1,
  "name": "AI行业周报",
  "prompt": "请综合这些新闻...",
  "temperature": 0.7,
  "style": "正式",
  "targetPlatform": "知乎",
  "latestVersion": 2,
  "latestContent": "（最新版本内容）",
  "createdAt": "...",
  "updatedAt": "...",
  "news": [
    {"id":1, "title":"AI 行业迎来新突破", "summary":"...", "sourceRssName":"36氪"}
  ]
}
```

### 20. PUT /drafts/{id}

更新稿件。校验同 #17。关联新闻先删后插。

### 21. DELETE /drafts/{id}

删除稿件及其关联数据。返回 204。

### 22. POST /drafts/{id}/generate

AI 生成稿件内容。

**流程**：
1. 加载 draft + 关联 News
2. 拼接 AI prompt（system + user + 素材文本）
3. 调用 AI API（配置来自 Settings）
4. 写入 `DraftVersion` → 更新 `Draft.latestVersion` + `latestContent`

**错误**：
- 400 — newsIds 为空
- 502 — AI 服务不可用

**响应**：
```json
{ "content": "生成的完整稿件...", "version": 3 }
```

---

## 六、RSS 源管理

### 23. GET /rss-sources

RSS 源列表（不分页）。

**查询参数**：`?groupId=1`（可选）

**响应**：
```json
[
  {
    "id": 1,
    "url": "https://36kr.com/feed",
    "name": "36氪",
    "iconPath": "/static/icons/1.png",
    "createdAt": "2026-05-15T10:00:00+08:00",
    "lastFetchAt": "2026-05-20T08:05:00+08:00",
    "totalFetched": 1234,
    "isActive": true,
    "groupIds": [1, 3],
    "groupNames": ["科技", "创投"]
  }
]
```

### 24. POST /rss-sources

添加 RSS 源。

**请求**：
```json
{
  "url": "https://example.com/rss",
  "name": "可选",
  "groupIds": [1, 2]
}
```

**流程**：
1. 校验 URL 格式、非空
2. 访问 RSS 地址，解析 XML
   - 成功 → 提取 title（如果未提供 name）+ 下载 favicon 存到 `/static/icons/{id}.{ext}`
   - 失败 → 400 "无法访问该 RSS 地址"
3. 查重：URL 已存在 → 409
4. 事务：INSERT RssSource + INSERT RssSourceGroup

**错误**：400（URL无效/RSS不可达）、409（已存在）

### 25. DELETE /rss-sources/{id}

软删除：`UPDATE rss_source SET is_active = FALSE`。News 历史数据不受影响。

返回 204。

### 26. POST /rss-sources/import-opml

导入 OPML 文件。

**请求**：`multipart/form-data`，字段 `file`

**流程**：
1. 解析 OPML XML
2. 遍历 outline：父级（text 无 xmlUrl）→ 创建/查找 RssGroup；子级（有 xmlUrl）→ url 已存在则跳过，不存在则创建
3. 不验证 RSS 可访问性

**响应**：
```json
{ "created": 15, "skipped": 3, "errors": [], "total": 18 }
```

### 27. GET /rss-sources/export-opml

导出 OPML 文件。

**响应**：`Content-Type: application/xml`，`Content-Disposition: attachment`

---

## 七、RSS 分组管理

### 28. GET /rss-groups

分组列表。

**响应**：
```json
[
  {"id":1, "name":"科技", "sourceCount":8, "createdAt":"..."},
  {"id":2, "name":"财经", "sourceCount":5, "createdAt":"..."}
]
```

### 29. POST /rss-groups

创建分组。

**请求**：`{ "name": "科技" }`
**错误**：400（名为空）、409（同名）

### 30. PUT /rss-groups/{id}

更新分组名。

**请求**：`{ "name": "新名称" }`
**错误**：404、409（新名与其他分组重名）

### 31. DELETE /rss-groups/{id}

删除分组，解除源关联（源本身不删）。返回 204。

---

## 八、设置

### 32. GET /settings

获取设置。

**响应**：
```json
{
  "taskIntervalHours": 6,
  "aiApiUrl": "https://api.openai.com/v1",
  "aiApiKey": "sk-****...****xYz",
  "aiModel": "gpt-4o",
  "defaultGroupId": 1,
  "updatedAt": "2026-05-20T10:00:00+08:00"
}
```
- `aiApiKey` 脱敏：前3 + `****` + 后3

### 33. PUT /settings

更新设置（全量替换）。

**请求**：
```json
{
  "taskIntervalHours": 6,
  "aiApiUrl": "https://api.openai.com/v1",
  "aiApiKey": "sk-new-key",
  "aiModel": "gpt-4o",
  "defaultGroupId": 1
}
```
- `aiApiKey`：传 `"******"` 或空字符串 → 不更新，保留旧值；传完整 key → 更新
- `taskIntervalHours`：0 表示禁用定时任务
- 校验：`taskIntervalHours >= 0`，`aiApiUrl` 合法 URL 格式
- UPSERT id=1

**响应**：更新后的 Settings（aiApiKey 脱敏）
