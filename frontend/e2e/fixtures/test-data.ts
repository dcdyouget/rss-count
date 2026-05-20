// ============================================================
// 测试夹具数据 — RSS XML / AI 响应 / OPML
// ============================================================

// ---------- RSS 2.0 XML（3 条新闻） ----------

export const MOCK_RSS_XML = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>科技资讯</title>
    <link>https://example.com/tech</link>
    <description>最新科技资讯</description>
    <item>
      <title>AI 突破：新模型超越人类表现</title>
      <link>https://example.com/tech/ai-breakthrough</link>
      <description>最新 AI 模型在多项基准测试中超越人类水平，标志着人工智能发展的里程碑。</description>
      <pubDate>Mon, 19 May 2026 08:00:00 GMT</pubDate>
      <source url="https://example.com/tech">科技资讯</source>
    </item>
    <item>
      <title>量子计算商业化加速</title>
      <link>https://example.com/tech/quantum-computing</link>
      <description>多家科技巨头宣布量子计算商用计划，预计明年推出首款实用型量子计算机。</description>
      <pubDate>Mon, 19 May 2026 07:30:00 GMT</pubDate>
      <source url="https://example.com/tech">科技资讯</source>
    </item>
    <item>
      <title>开源社区迎来新协作平台</title>
      <link>https://example.com/tech/open-source</link>
      <description>全新开源协作平台发布，整合代码托管、CI/CD 和项目管理功能。</description>
      <pubDate>Mon, 19 May 2026 07:00:00 GMT</pubDate>
      <source url="https://example.com/tech">科技资讯</source>
    </item>
  </channel>
</rss>`;

export const MOCK_AI_SUMMARY = 'AI 突破、量子计算、开源协作等科技领域的最新进展综述。';

export const MOCK_AI_TAGS = ['AI', '量子计算', '开源', '科技'];

export const MOCK_AI_DRAFT_CONTENT = `# 科技周报：AI 突破与量子计算进展

## AI 突破：新模型超越人类表现

最新 AI 模型在多项基准测试中超越人类水平，标志着人工智能发展的里程碑。该模型在自然语言理解、图像识别和复杂推理等任务上均取得突破性进展。

## 量子计算商业化加速

多家科技巨头宣布量子计算商用计划，预计明年推出首款实用型量子计算机。

## 开源社区迎来新协作平台

全新开源协作平台发布，整合代码托管、CI/CD 和项目管理功能。`;

// ---------- 标准 OPML（2 分组 10 源） ----------

export const MOCK_OPML_STANDARD = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head>
    <title>RSS Count 订阅源</title>
  </head>
  <body>
    <outline text="科技" title="科技">
      <outline type="rss" text="36氪" title="36氪" xmlUrl="https://36kr.com/feed" htmlUrl="https://36kr.com"/>
      <outline type="rss" text="少数派" title="少数派" xmlUrl="https://sspai.com/feed" htmlUrl="https://sspai.com"/>
      <outline type="rss" text="阮一峰网络日志" title="阮一峰网络日志" xmlUrl="https://ruanyifeng.com/feed.xml" htmlUrl="https://ruanyifeng.com"/>
      <outline type="rss" text="Linux 中国" title="Linux 中国" xmlUrl="https://linux.cn/rss" htmlUrl="https://linux.cn"/>
      <outline type="rss" text="InfoQ" title="InfoQ" xmlUrl="https://infoq.com/feed" htmlUrl="https://infoq.com"/>
    </outline>
    <outline text="设计" title="设计">
      <outline type="rss" text="优设" title="优设" xmlUrl="https://uisdc.com/feed" htmlUrl="https://uisdc.com"/>
      <outline type="rss" text="站酷" title="站酷" xmlUrl="https://zcool.com.cn/feed" htmlUrl="https://zcool.com.cn"/>
      <outline type="rss" text="UI 中国" title="UI 中国" xmlUrl="https://ui.cn/feed" htmlUrl="https://ui.cn"/>
      <outline type="rss" text="Behance 精选" title="Behance 精选" xmlUrl="https://behance.net/feed" htmlUrl="https://behance.net"/>
      <outline type="rss" text="Dribbble 热门" title="Dribbble 热门" xmlUrl="https://dribbble.com/feed" htmlUrl="https://dribbble.com"/>
    </outline>
  </body>
</opml>`;

export const MOCK_OPML_NO_GROUP = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head>
    <title>无分组源</title>
  </head>
  <body>
    <outline type="rss" text="独立博客 A" title="独立博客 A" xmlUrl="https://blog-a.com/feed" htmlUrl="https://blog-a.com"/>
    <outline type="rss" text="独立博客 B" title="独立博客 B" xmlUrl="https://blog-b.com/feed" htmlUrl="https://blog-b.com"/>
    <outline type="rss" text="独立博客 C" title="独立博客 C" xmlUrl="https://blog-c.com/feed" htmlUrl="https://blog-c.com"/>
  </body>
</opml>`;

export const MOCK_OPML_EMPTY = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head>
    <title>空订阅源</title>
  </head>
  <body>
  </body>
</opml>`;

// ---------- AI Mock Response JSON ----------

export const MOCK_AI_GENERATE_RESPONSE = {
  content: MOCK_AI_DRAFT_CONTENT,
  version: 1,
};

// ---------- SSE Event Sequences ----------

export const SSE_EVENTS_NORMAL = [
  `event: progress\ndata: {"pulling":{"done":false,"currentSource":"科技资讯","sourceProgress":"0/3"},"formatting":{"done":false}}\n`,
  `event: progress\ndata: {"pulling":{"done":false,"currentSource":"科技资讯","sourceProgress":"3/3"},"formatting":{"done":false,"formatted":0,"total":3,"currentAction":"格式化中..."}}\n`,
  `event: progress\ndata: {"pulling":{"done":true,"totalFetched":3},"formatting":{"done":true,"formatted":3,"total":3}}\n`,
  `event: complete\ndata: {"reportId":1}\n`,
];

export const SSE_EVENTS_PARTIAL = [
  `event: progress\ndata: {"pulling":{"done":false,"currentSource":"科技资讯","sourceProgress":"1/3"},"formatting":{"done":false}}\n`,
];

export const SSE_EVENTS_ERROR = [
  `event: progress\ndata: {"pulling":{"done":false,"currentSource":"科技资讯","sourceProgress":"0/3"},"formatting":{"done":false}}\n`,
  `event: error\ndata: {"message":"RSS 源连接超时"}\n`,
];

// ---------- Mock Structured Content ----------

export const MOCK_STRUCTURED_CONTENT = [
  { type: 'heading', level: 1, text: 'AI 突破：新模型超越人类表现' },
  { type: 'paragraph', text: '最新 AI 模型在多项基准测试中超越人类水平，标志着人工智能发展的里程碑。' },
  { type: 'heading', level: 2, text: '关键发现' },
  { type: 'paragraph', text: '该模型在自然语言理解、图像识别和复杂推理等任务上均取得突破性进展。' },
  { type: 'blockquote', text: '"这是 AI 领域过去十年最重要的突破" —— 业界专家' },
  { type: 'paragraph', text: '研究人员表示，新架构在保持推理能力的同时，显著降低了计算资源需求。' },
];
