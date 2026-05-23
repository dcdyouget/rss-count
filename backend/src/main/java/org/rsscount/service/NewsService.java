package org.rsscount.service;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.rsscount.entity.News;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class NewsService {

    public record PagedResponse<T>(
        long total,
        int page,
        int size,
        List<T> items
    ) {}

    public record NewsListItem(
        Long id,
        String title,
        String summary,
        String headerImageHtml,
        String sourceRssName,
        String reportName,
        Long reportId,
        boolean isRead,
        boolean inMaterialPile,
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

    public record BatchMaterialPileRequest(
        List<Long> newsIds,
        String action  // ADD or REMOVE
    ) {}

    public record MaterialPileItem(
        Long id,
        String title,
        LocalDateTime materialPileAddedAt
    ) {}

    // ---- List with pagination and filters ----

    public PagedResponse<NewsListItem> list(int page, int size, String keyword,
                                             String reportName, Boolean isRead) {
        StringBuilder query = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();

        if (keyword != null && !keyword.isBlank()) {
            query.append(" and title like :keyword");
            params.put("keyword", "%" + keyword + "%");
        }

        if (isRead != null) {
            query.append(" and isRead = :isRead");
            params.put("isRead", isRead);
        }

        // For reportName filter, we need to join with Report
        // Simplified: filter by report name if needed
        String baseQuery = query.toString();

        PanacheQuery<News> panacheQuery;
        if (reportName != null && !reportName.isBlank()) {
            // Find matching report IDs first
            List<org.rsscount.entity.Report> reports = org.rsscount.entity.Report
                .find("name like ?1", "%" + reportName + "%").list();
            if (reports.isEmpty()) {
                return new PagedResponse<>(0, page, size, List.of());
            }
            List<Long> reportIds = reports.stream().map(r -> r.id).toList();
            String reportFilter = baseQuery + " and report.id in :reportIds";
            params.put("reportIds", reportIds);
            panacheQuery = News.find(reportFilter, Sort.by("createdAt").descending(), params);
        } else {
            panacheQuery = News.find(baseQuery, Sort.by("createdAt").descending(), params);
        }

        long total = panacheQuery.count();
        List<News> newsList = panacheQuery.page(Page.of(page - 1, size)).list();

        List<NewsListItem> items = newsList.stream()
            .map(this::toListItem)
            .collect(Collectors.toList());

        return new PagedResponse<>(total, page, size, items);
    }

    // ---- Detail ----

    public NewsDetail getDetail(Long id) {
        News news = News.findById(id);
        if (news == null) {
            throw new NotFoundException("新闻不存在");
        }
        return toDetail(news);
    }

    // ---- Mark as read ----

    @Transactional
    public void markAsRead(Long id) {
        News news = News.findById(id);
        if (news == null) {
            throw new NotFoundException("新闻不存在");
        }
        news.isRead = true;
        news.persist();
    }

    // ---- Batch material pile ----

    @Transactional
    public Map<String, Object> batchMaterialPile(BatchMaterialPileRequest request) {
        if (request.newsIds == null || request.newsIds.isEmpty()) {
            throw new IllegalArgumentException("newsIds不能为空");
        }
        if (request.action == null || (!request.action.equals("ADD") && !request.action.equals("REMOVE"))) {
            throw new IllegalArgumentException("action必须为ADD或REMOVE");
        }

        int affected = 0;
        boolean add = "ADD".equals(request.action);

        for (Long newsId : request.newsIds) {
            News news = News.findById(newsId);
            if (news != null && news.inMaterialPile != add) {
                news.inMaterialPile = add;
                news.materialPileAddedAt = add ? LocalDateTime.now() : null;
                news.persist();
                affected++;
            }
        }

        return Map.of("affected", affected);
    }

    // ---- Material pile list ----

    public PagedResponse<MaterialPileItem> materialPileList(int page, int size) {
        PanacheQuery<News> query = News.find(
            "inMaterialPile = true",
            Sort.by("materialPileAddedAt").descending()
        );

        long total = query.count();
        List<News> newsList = query.page(Page.of(page - 1, size)).list();

        List<MaterialPileItem> items = newsList.stream()
            .map(n -> new MaterialPileItem(n.id, n.title, n.materialPileAddedAt))
            .collect(Collectors.toList());

        return new PagedResponse<>(total, page, size, items);
    }

    // ---- Private helpers ----

    private NewsListItem toListItem(News news) {
        String reportName = null;
        Long reportId = null;
        if (news.report != null) {
            reportName = news.report.name;
            reportId = news.report.id;
        }

        return new NewsListItem(
            news.id,
            news.title,
            news.summary,
            news.headerImageHtml,
            news.sourceRssName,
            reportName,
            reportId,
            news.isRead,
            news.inMaterialPile,
            news.publishedAt
        );
    }

    private NewsDetail toDetail(News news) {
        // Get tags from news_tag + tag tables
        List<String> tags;
        try {
            List<org.rsscount.entity.NewsTag> newsTags = org.rsscount.entity.NewsTag
                .find("newsId", news.id).list();
            if (newsTags != null && !newsTags.isEmpty()) {
                List<Long> tagIds = newsTags.stream().map(nt -> nt.tagId).toList();
                List<org.rsscount.entity.Tag> tagEntities = org.rsscount.entity.Tag
                    .find("id in ?1", tagIds).list();
                tags = tagEntities.stream()
                    .map(t -> t.name)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } else {
                tags = new ArrayList<>();
            }
        } catch (Exception e) {
            // Tags are optional, ignore errors
            tags = new ArrayList<>();
        }

        return new NewsDetail(
            news.id,
            news.title,
            news.author,
            news.sourceRssName,
            news.sourceUrl,
            news.publishedAt,
            tags,
            news.isRead,
            news.inMaterialPile,
            news.structuredContent
        );
    }
}
