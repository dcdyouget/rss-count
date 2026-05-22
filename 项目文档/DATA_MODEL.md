# RSS Count 数据模型文档

## ER 关系

```
RssGroup N──M RssSourceGroup N──M RssSource
                       │ ON DELETE SET NULL
Task 1:1 Report 1:N News N──M NewsTag N──M Tag
                       │ N──M DraftNews N──M Draft 1:N DraftVersion
Settings (单行)
```

## 实体清单（12 个 + 3 个关联表）

### RssGroup
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| name | String(100) NOT NULL UNIQUE | 分组名 |
| createdAt | LocalDateTime | |

### RssSource
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| url | String(2048) NOT NULL UNIQUE | RSS地址 |
| name | String(200) NOT NULL | 源名称 |
| iconPath | String(500) | 图标路径 |
| etag | String(512) | HTTP ETag |
| lastModified | String(512) | HTTP Last-Modified |
| lastFetchAt | LocalDateTime | 最近拉取 |
| totalFetched | int | 累计拉取数 |
| isActive | boolean | 软删除标记 |
| createdAt | LocalDateTime | |

### RssSourceGroup（关联表）
| 字段 | 类型 |
|------|------|
| rssSourceId | Long PK FK→RssSource |
| rssGroupId | Long PK FK→RssGroup |

### Task
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| name | String(200) NOT NULL | |
| timeRangeStart | LocalDateTime NOT NULL | |
| timeRangeEnd | LocalDateTime NOT NULL | |
| status | String(20) NOT NULL | RUNNING/COMPLETED/FAILED |
| sourceType | String(10) NOT NULL | ALL/GROUP/SOURCE/MIXED |
| sourceConfig | TEXT | JSON配置 |
| startedAt | LocalDateTime | |
| endedAt | LocalDateTime | |
| errorMessage | String(2000) | |
| createdAt | LocalDateTime | |

### Report
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| task | ManyToOne→Task UNIQUE | 1:1 |
| name | String(200) NOT NULL | |
| timeRangeStart/End | LocalDateTime | |
| newsCount | int default 0 | |
| createdAt | LocalDateTime | |

### News（核心实体）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| report | ManyToOne→Report NOT NULL | |
| title | String(500) NOT NULL | 清洗后 |
| summary | TEXT | AI摘要 |
| author | String(100) default "未知" | |
| rawContent | TEXT | 原始HTML |
| structuredContent | TEXT | 清洗后HTML |
| sourceUrl | String(2048) | 原文链接 |
| headerImageUrl | String(2048) | 头图本地路径 |
| sourceRssName | String(200) | 来源名 |
| category | String(50) | |
| publishedAt | LocalDateTime | UTC+8 |
| simHash | Long | 64位指纹 |
| contentLength | int | |
| isRead | boolean default false | |
| inMaterialPile | boolean default false | |
| materialPileAddedAt | LocalDateTime | |
| createdAt | LocalDateTime | |

### Tag
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| name | String(50) NOT NULL UNIQUE | |
| createdAt | LocalDateTime | |

### NewsTag（关联表）
PK: (newsId, tagId)

### Draft
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK IDENTITY | |
| name | String(200) NOT NULL | |
| prompt | TEXT | 用户提示词 |
| temperature | double default 0.7 | |
| style | String(50) | 文稿风格 |
| targetPlatform | String(50) | 目标平台 |
| latestVersion | int default 0 | |
| latestContent | TEXT | |
| createdAt/updatedAt | LocalDateTime | |

### DraftVersion
| 字段 | 类型 |
|------|------|
| id | Long PK IDENTITY |
| draft | ManyToOne→Draft |
| version | int |
| content | TEXT |
| createdAt | LocalDateTime |

UNIQUE(draft_id, version)

### DraftNews（关联表）
PK: (draftId, newsId), sortOrder: int

### Settings（单行 id=1）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long PK | 恒为 1 |
| taskIntervalHours | int default 6 | 定时间隔 |
| aiApiUrl | String(500) | |
| aiApiKey | String(200) | 脱敏回显 |
| aiModel | String(100) | |
| defaultGroup | ManyToOne→RssGroup ON DELETE SET NULL | |
| updatedAt | LocalDateTime | |

## IDENTITY ID 策略

所有实体使用 `@GeneratedValue(strategy = GenerationType.IDENTITY)`：
- SQLite AUTOINCREMENT 原生支持
- 无需序列表 → 无行锁竞争
- 批量 INSERT 时独立获取 ID，不成瓶颈

## structuredContent 格式

清洗后的正文存储为安全 HTML 子集：
- JSoup `Safelist.relaxed()` 过滤
- 图片 src 替换为本地路径 `/static/images/{uuid}.{ext}`
- a[target=_blank] + a[rel=noopener noreferrer]
- 噪声节点全部移除
