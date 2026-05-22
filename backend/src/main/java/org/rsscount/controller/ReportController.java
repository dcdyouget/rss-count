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

@Path("/api/v1/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportController {

    @Inject
    NewsService newsService;

    // ── DTOs ───────────────────────────────────────────────

    public record PagedResponse<T>(long total, int page, int size, List<T> items) {}

    public record ReportListItem(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        int newsCount,
        LocalDateTime createdAt
    ) {}

    public record ReportDetail(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        int newsCount,
        LocalDateTime createdAt,
        List<NewsBrief> news
    ) {}

    public record NewsBrief(
        Long id,
        String title,
        String summary,
        String headerImageUrl,
        String sourceRssName,
        LocalDateTime publishedAt
    ) {}

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
                n.id, n.title, n.summary, n.headerImageUrl,
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
