package org.rsscount.service;

import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.rsscount.entity.News;
import org.rsscount.entity.Report;
import org.rsscount.entity.RssSource;
import org.rsscount.entity.Task;
import org.rsscount.util.SimHash;
import org.rsscount.util.TextCleaner;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RSS 源拉取服务 — 并发拉取、三级去重、结果持久化。
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>RSS 解析：使用 ROME 解析 RSS 2.0 / Atom feed</li>
 *   <li>并发拉取：Virtual Threads 每源一线程</li>
 *   <li>三级去重：URL → 清洗标题 → SimHash（汉明距离 ≤ 3）</li>
 *   <li>异常隔离：单个源失败不影响其他源</li>
 * </ul>
 */
@ApplicationScoped
public class RssFetchService {

    /** 单个源拉取超时 (秒) */
    static final long FETCH_TIMEOUT_SECONDS = 30;

    /** SimHash 汉明距离阈值：≤ 此值判定为重复 */
    public static final int SIMHASH_DISTANCE_THRESHOLD = 3;

    // ── 公开类型 ──────────────────────────────────────────

    /** 多源拉取聚合结果 */
    public record FetchResult(
        int newNews,
        int duplicates,
        int failedSources,
        List<String> errors
    ) {
        public static FetchResult empty() {
            return new FetchResult(0, 0, 0, List.of());
        }
    }

    /** 三级去重检查结果 */
    public record DuplicateCheckResult(
        boolean isDuplicate,
        News existing,
        int level       // 1=URL, 2=CleanTitle, 3=SimHash
    ) {
        static DuplicateCheckResult notDuplicate() {
            return new DuplicateCheckResult(false, null, 0);
        }

        static DuplicateCheckResult duplicate(News existing, int level) {
            return new DuplicateCheckResult(true, existing, level);
        }
    }

    // ── 公开方法 ──────────────────────────────────────────

    /**
     * 并发拉取多个 RSS 源。
     *
     * <p>使用 Virtual Threads，每源独立线程 + 独立事务。
     * 单个源失败不影响其他源。</p>
     *
     * @param sources 待拉取源列表
     * @param task    关联任务
     * @param report  关联报告
     * @return 聚合拉取结果
     */
    public FetchResult fetchAll(List<RssSource> sources, Task task, Report report) {
        if (sources == null || sources.isEmpty()) {
            return FetchResult.empty();
        }

        // 通过 CDI 代理调用，确保 @Transactional 生效
        RssFetchService proxy = getTransactionalProxy();
        return doFetchAll(sources, report, proxy);
    }

    /**
     * 从单个 RSS 源拉取新闻。
     *
     * <p>独立事务，使用 ROME 解析 RSS 2.0/Atom feed，
     * 每拉取到一条新闻立即执行三级去重。</p>
     *
     * @param source RSS 源
     * @param task   关联任务
     * @param report 关联报告
     * @return 新增（非重复）新闻数量
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int fetchFromSource(RssSource source, Task task, Report report) {
        return doFetchFromSource(source, report);
    }

    /**
     * 三级去重检查 — 纯逻辑，无数据库依赖。
     *
     * <ol>
     *   <li>URL 精确匹配 → level 1，直接丢弃新条目</li>
     *   <li>清洗标题精确匹配 → level 2，保留 contentLength 更长者</li>
     *   <li>SimHash 汉明距离 ≤ 3 → level 3，保留 contentLength 更长者</li>
     * </ol>
     *
     * @param candidate         候选新闻
     * @param existingInReport  当前报告已拉取的新闻列表
     * @return 去重结果
     */
    public DuplicateCheckResult checkDuplicate(News candidate, List<News> existingInReport) {
        if (existingInReport == null || existingInReport.isEmpty()) {
            return DuplicateCheckResult.notDuplicate();
        }

        String cleanCandidateTitle = TextCleaner.clean(candidate.title);

        for (News existing : existingInReport) {
            // Level 1: URL 精确匹配
            if (candidate.sourceUrl != null && !candidate.sourceUrl.isBlank()
                && candidate.sourceUrl.equals(existing.sourceUrl)) {
                return DuplicateCheckResult.duplicate(existing, 1);
            }

            // Level 2: 清洗后标题精确匹配
            String cleanExistingTitle = TextCleaner.clean(existing.title);
            if (!cleanCandidateTitle.isEmpty() && cleanCandidateTitle.equals(cleanExistingTitle)) {
                return DuplicateCheckResult.duplicate(existing, 2);
            }
        }

        // Level 3: SimHash 汉明距离匹配
        if (candidate.simHash != null && candidate.simHash != 0L) {
            for (News existing : existingInReport) {
                if (existing.simHash != null && existing.simHash != 0L) {
                    int distance = SimHash.hammingDistance(candidate.simHash, existing.simHash);
                    if (distance <= SIMHASH_DISTANCE_THRESHOLD) {
                        return DuplicateCheckResult.duplicate(existing, 3);
                    }
                }
            }
        }

        return DuplicateCheckResult.notDuplicate();
    }

    // ── 包级可见方法（测试用 + 内部用）─────────────────────

    /**
     * 获取 CDI 事务代理，确保 @Transactional 生效。
     * 包级可见以便测试时覆盖。
     */
    RssFetchService getTransactionalProxy() {
        return Arc.container().instance(RssFetchService.class).get();
    }

    /**
     * 执行多源并发拉取（包级可见，测试可直接传入 mock 代理）。
     *
     * @param sources 源列表
     * @param report  关联报告
     * @param proxy   事务代理（调用其 fetchFromSource）
     */
    FetchResult doFetchAll(List<RssSource> sources, Report report, RssFetchService proxy) {
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalNew = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();

            for (RssSource source : sources) {
                futures.add(executor.submit(() -> {
                    try {
                        int count = proxy.fetchFromSource(source, null, report);
                        totalNew.addAndGet(count);
                        return count;
                    } catch (Exception e) {
                        String msg = String.format("%s (url=%s): %s",
                            source.name, source.url, e.getMessage());
                        errors.add(msg);
                        failed.incrementAndGet();
                        Log.errorf("Source fetch failed: %s", msg);
                        return 0;
                    }
                }));
            }

            // 等待全部完成
            for (Future<Integer> future : futures) {
                try {
                    future.get(FETCH_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.add("拉取过程被中断");
                } catch (ExecutionException e) {
                    errors.add("拉取过程异常: " + e.getCause().getMessage());
                } catch (java.util.concurrent.TimeoutException e) {
                    errors.add("拉取超时");
                }
            }
        }

        // 更新报告新闻总数
        updateReportNewsCount(report);

        return new FetchResult(totalNew.get(), 0, failed.get(), errors);
    }

    /**
     * 单源拉取核心逻辑（包级可见，便于测试）。
     *
     * @param source RSS 源
     * @param report 关联报告
     * @return 新增数量
     */
    int doFetchFromSource(RssSource source, Report report) {
        Log.infof("开始拉取: %s (%s)", source.name, source.url);

        List<News> existingNews = loadExistingNews(report);
        int newCount = 0;
        int totalEntries = 0;

        try {
            SyndFeed feed = parseFeed(source.url);
            List<SyndEntry> entries = feed.getEntries();

            if (entries == null || entries.isEmpty()) {
                Log.infof("源 %s 无条目", source.name);
            } else {
                for (SyndEntry entry : entries) {
                    totalEntries++;
                    try {
                        News news = syndEntryToNews(entry, source, report);
                        if (news == null || news.title == null || news.title.isBlank()) {
                            continue;
                        }

                        DuplicateCheckResult check = checkDuplicate(news, existingNews);

                        if (check.isDuplicate()) {
                            handleDuplicate(news, check);
                        } else {
                            if (news.isPersistent()) {
                                // already managed
                                news.persist();
                            } else {
                                news.persist();
                            }
                            existingNews.add(news);
                            newCount++;
                        }
                    } catch (Exception e) {
                        Log.debugf("跳过无法解析的条目 [%s]: %s", source.name, e.getMessage());
                    }
                }
            }

            // 更新源统计
            source.lastFetchAt = LocalDateTime.now();
            source.totalFetched += newCount;
            // persist handled by caller's transaction

            Log.infof("拉取完成: %s — 总条目 %d, 新增 %d", source.name, totalEntries, newCount);
        } catch (Exception e) {
            Log.errorf("拉取失败 %s: %s", source.name, e.getMessage());
            throw new RuntimeException("拉取失败: " + source.name, e);
        }

        return newCount;
    }

    /**
     * 解析 RSS feed URL 为 SyndFeed。
     * 包级可见以便测试 Mock。
     */
    SyndFeed parseFeed(String url) throws Exception {
        URL feedUrl = new URL(url);
        SyndFeedInput input = new SyndFeedInput();
        return input.build(new XmlReader(feedUrl));
    }

    /**
     * 将 ROME SyndEntry 转换为 News 实体。
     * 包级可见以便测试独立调用。
     *
     * @param entry  ROME feed 条目
     * @param source RSS 源
     * @param report 关联报告
     * @return 转换后的 News（未持久化）
     */
    News syndEntryToNews(SyndEntry entry, RssSource source, Report report) {
        News news = new News();
        news.report = report;
        news.sourceRss = source;
        news.sourceRssName = source.name;

        // 标题 — 清洗后存储
        String rawTitle = entry.getTitle();
        news.title = (rawTitle != null && !rawTitle.isBlank())
            ? TextCleaner.clean(rawTitle)
            : "";

        // 链接
        news.sourceUrl = entry.getLink();

        // 概览 — 优先 description
        SyndContent description = entry.getDescription();
        if (description != null && description.getValue() != null) {
            news.summary = description.getValue();
        }

        // 正文 — 优先 content:encoded (contents[0])，其次 description
        String rawContent = null;
        List<SyndContent> contents = entry.getContents();
        if (contents != null && !contents.isEmpty()) {
            rawContent = contents.get(0).getValue();
        } else if (description != null && description.getValue() != null) {
            rawContent = description.getValue();
        }
        news.rawContent = rawContent;
        news.contentLength = (rawContent != null) ? rawContent.length() : 0;

        // 作者
        String author = entry.getAuthor();
        news.author = (author != null && !author.isBlank()) ? author : "未知";

        // 发布时间 — 规范化为 UTC+8
        if (entry.getPublishedDate() != null) {
            news.publishedAt = entry.getPublishedDate().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime();
        }

        // 类别
        List<SyndCategory> categories = entry.getCategories();
        if (categories != null && !categories.isEmpty()) {
            news.category = categories.get(0).getName();
        }

        // SimHash — 从标题 + 正文前 500 字符计算
        StringBuilder simHashInput = new StringBuilder(news.title);
        if (rawContent != null && !rawContent.isEmpty()) {
            simHashInput.append(' ');
            int limit = Math.min(500, rawContent.length());
            simHashInput.append(rawContent, 0, limit);
        }
        news.simHash = SimHash.compute(simHashInput.toString());

        return news;
    }

    // ── 内部实现 ──────────────────────────────────────────

    /** 加载报告下已有新闻列表 */
    List<News> loadExistingNews(Report report) {
        return News.find("report.id", report.id).list();
    }

    /**
     * 处理重复新闻：
     * <ul>
     *   <li>Level 1 (URL)：直接丢弃</li>
     *   <li>Level 2-3：保留 contentLength 更长者</li>
     * </ul>
     */
    void handleDuplicate(News candidate, DuplicateCheckResult check) {
        if (check.level() == 1) {
            Log.debugf("URL 去重命中: %s", candidate.sourceUrl);
            return;
        }

        if (candidate.contentLength > check.existing().contentLength) {
            News managed = News.findById(check.existing().id);
            if (managed != null) {
                managed.title = candidate.title;
                managed.summary = candidate.summary;
                managed.author = candidate.author;
                managed.rawContent = candidate.rawContent;
                managed.contentLength = candidate.contentLength;
                managed.simHash = candidate.simHash;
                managed.sourceUrl = candidate.sourceUrl;
                managed.headerImageUrl = candidate.headerImageUrl;
                managed.publishedAt = candidate.publishedAt;
                Log.debugf("去重(level %d)保留更长正文: %s", check.level(), candidate.title);
            }
        }
    }

    /** 更新报告新闻总数 */
    void updateReportNewsCount(Report report) {
        Report managed = Report.findById(report.id);
        if (managed != null) {
            long count = News.count("report.id", managed.id);
            managed.newsCount = (int) count;
        }
    }
}
