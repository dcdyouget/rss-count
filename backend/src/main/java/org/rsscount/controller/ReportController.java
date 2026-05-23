package org.rsscount.controller;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.rsscount.entity.News;
import org.rsscount.entity.Report;
import org.rsscount.entity.Tag;
import org.rsscount.service.NewsService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 报告管理 REST 接口。
 * 路由前缀: /api/v1/reports
 * 负责报告的查询、详情查看以及报告内新闻的查阅。
 */
@Path("/api/v1/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportController {

    @Inject
    NewsService newsService;

    // ── DTOs ───────────────────────────────────────────────

    /**
     * 通用分页响应包装。
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页条数
     * @param items 当前页数据列表
     */
    public record PagedResponse<T>(long total, int page, int size, List<T> items) {}

    /**
     * 报告列表项 DTO。
     * @param id 报告ID
     * @param name 报告名称
     * @param timeRangeStart 报告时间范围（开始）
     * @param timeRangeEnd 报告时间范围（结束）
     * @param newsCount 报告中的新闻数量
     * @param createdAt 报告创建时间
     */
    public record ReportListItem(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        int newsCount,
        LocalDateTime createdAt
    ) {}

    /**
     * 报告详情 DTO，包含新闻列表。
     * @param id 报告ID
     * @param name 报告名称
     * @param timeRangeStart 报告时间范围（开始）
     * @param timeRangeEnd 报告时间范围（结束）
     * @param newsCount 新闻数量
     * @param createdAt 报告创建时间
     * @param news 新闻概要列表（按发布时间倒序）
     */
    public record ReportDetail(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        int newsCount,
        LocalDateTime createdAt,
        List<NewsBrief> news
    ) {}

    /**
     * 新闻概要 DTO。
     * @param id 新闻ID
     * @param title 新闻标题
     * @param summary 新闻摘要
     * @param headerImageHtml 头图 HTML
     * @param sourceRssName 来源 RSS 名称
     * @param publishedAt 发布时间
     */
    public record NewsBrief(
        Long id,
        String title,
        String summary,
        String headerImageHtml,
        String sourceRssName,
        LocalDateTime publishedAt
    ) {}

    /**
     * 新闻详情 DTO。
     * @param id 新闻ID
     * @param title 新闻标题
     * @param author 作者
     * @param sourceRssName 来源 RSS 名称
     * @param sourceUrl 原文链接
     * @param publishedAt 发布时间
     * @param tags 标签列表
     * @param isRead 是否已读
     * @param inMaterialPile 是否在素材堆中
     * @param structuredContent 结构化内容（HTML）
     */
    public record NewsDetail(
        Long id,
        String title,
        String author,
        String sourceRssName,
        String sourceUrl,
        LocalDateTime publishedAt,
        List<String> tags,
        boolean isRead,
        boolean inMaterialPile,
        String structuredContent
    ) {}

    // ── 10. GET /reports — List with pagination ────────────

    /**
     * 分页查询报告列表，按创建时间倒序排列。
     * @param page 页码（1-based）
     * @param size 每页条数，默认20
     * @return 分页响应，包含报告列表
     */
    @GET
    public PagedResponse<ReportListItem> list(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("20") int size
    ) {
        var query = Report.findAll(Sort.by("createdAt").descending());
        long total = query.count();
        List<Report> reports = query.page(Page.of(page - 1, size)).list();

        List<ReportListItem> items = reports.stream()
            .map(r -> new ReportListItem(
                r.id, r.name, r.timeRangeStart, r.timeRangeEnd,
                r.newsCount, r.createdAt))
            .collect(Collectors.toList());

        return new PagedResponse<>(total, page, size, items);
    }

    // ── 11. GET /reports/{id} — Detail with news list ─────

    /**
     * 获取报告详情，包含报告内的全部新闻列表。
     * @param id 报告ID
     * @return 报告详情，含新闻概要列表
     * @throws NotFoundException 报告不存在时抛出
     */
    @GET
    @Path("/{id}")
    public ReportDetail getDetail(@PathParam("id") Long id) {
        Report report = Report.findById(id);
        if (report == null) {
            throw new NotFoundException("报告不存在");
        }

        // Load all news for this report (full list for frontend virtual scrolling)
        List<News> newsList = News.find(
            "report.id = ?1 order by publishedAt desc nulls last, createdAt desc",
            report.id
        ).list();

        List<NewsBrief> newsBriefs = newsList.stream()
            .map(n -> new NewsBrief(
                n.id, n.title, n.summary, n.headerImageHtml,
                n.sourceRssName, n.publishedAt))
            .collect(Collectors.toList());

        return new ReportDetail(
            report.id, report.name,
            report.timeRangeStart, report.timeRangeEnd,
            report.newsCount, report.createdAt,
            newsBriefs
        );
    }

    // ── 12. GET /reports/{id}/news/{newsId} ────────────────

    /**
     * 获取指定报告内某条新闻的详细内容。
     * @param reportId 报告ID
     * @param newsId 新闻ID
     * @return 新闻详情，含标签、阅读状态和结构化内容
     * @throws NotFoundException 新闻不存在或不属于该报告时抛出
     */
    @GET
    @Path("/{id}/news/{newsId}")
    public NewsDetail getNewsDetail(
        @PathParam("id") Long reportId,
        @PathParam("newsId") Long newsId
    ) {
        News news = News.findById(newsId);
        if (news == null) {
            throw new NotFoundException("新闻不存在");
        }

        // Verify news belongs to the specified report
        if (news.report == null || !news.report.id.equals(reportId)) {
            throw new NotFoundException("该新闻不属于指定报告");
        }

        // Get tags
        List<String> tags;
        try {
            List<org.rsscount.entity.NewsTag> newsTags =
                org.rsscount.entity.NewsTag.find("newsId", news.id).list();
            if (newsTags != null && !newsTags.isEmpty()) {
                List<Long> tagIds = newsTags.stream().map(nt -> nt.tagId).toList();
                List<Tag> tagEntities = Tag.find("id in ?1", tagIds).list();
                tags = tagEntities.stream()
                    .map(t -> t.name)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } else {
                tags = new ArrayList<>();
            }
        } catch (Exception e) {
            // Tags are optional
            tags = new ArrayList<>();
        }

        return new NewsDetail(
            news.id, news.title, news.author,
            news.sourceRssName, news.sourceUrl,
            news.publishedAt, tags,
            news.isRead, news.inMaterialPile,
            news.structuredContent
        );
    }
}
