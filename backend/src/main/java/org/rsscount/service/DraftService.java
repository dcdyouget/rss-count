package org.rsscount.service;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jsoup.Jsoup;
import org.rsscount.entity.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing drafts and AI-powered content generation.
 */
@ApplicationScoped
public class DraftService {

    @Inject
    AiService aiService;

    // ──────────────────────────────────────────────
    // DTOs
    // ──────────────────────────────────────────────

    public record PaginatedResponse<T>(
        long total,
        int page,
        int size,
        List<T> items
    ) {}

    public record CreateDraftRequest(
        String name,
        List<Long> newsIds,
        String prompt,
        double temperature,
        String style,
        String targetPlatform
    ) {}

    public record UpdateDraftRequest(
        String name,
        List<Long> newsIds,
        String prompt,
        double temperature,
        String style,
        String targetPlatform
    ) {}

    public record NewsBrief(
        Long id,
        String title,
        String summary,
        String sourceRssName
    ) {}

    public record DraftResponse(
        Long id,
        String name,
        String prompt,
        double temperature,
        String style,
        String targetPlatform,
        int latestVersion,
        String latestContent,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<NewsBrief> news
    ) {}

    public record DraftListSummary(
        Long id,
        String name,
        String style,
        String targetPlatform,
        int newsCount,
        int latestVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record DraftVersionInfo(
        int version,
        String content,
        LocalDateTime createdAt
    ) {}

    public record GenerateResponse(
        String content,
        int version
    ) {}

    public record MaterialPileItem(
        Long id,
        String title,
        LocalDateTime materialPileAddedAt
    ) {}

    // ──────────────────────────────────────────────
    // CRUD: Create
    // ──────────────────────────────────────────────

    @Transactional
    public DraftResponse create(CreateDraftRequest request) {
        if (request.name == null || request.name.isBlank()) {
            throw new IllegalArgumentException("稿件名称不能为空");
        }
        if (request.newsIds == null || request.newsIds.isEmpty()) {
            throw new IllegalArgumentException("newsIds不能为空");
        }
        if (request.temperature < 0.0 || request.temperature > 2.0) {
            throw new IllegalArgumentException("temperature必须在0.0到2.0之间");
        }

        Draft draft = new Draft();
        draft.name = request.name;
        draft.prompt = request.prompt;
        draft.temperature = request.temperature;
        draft.style = request.style;
        draft.targetPlatform = request.targetPlatform;
        draft.latestVersion = 0;
        draft.persist();

        // Create DraftNews associations
        saveDraftNewsAssociations(draft.id, request.newsIds);

        return toDraftResponse(draft);
    }

    // ──────────────────────────────────────────────
    // CRUD: Update
    // ──────────────────────────────────────────────

    @Transactional
    public DraftResponse update(Long id, UpdateDraftRequest request) {
        Draft draft = Draft.findById(id);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }

        if (request.name != null && !request.name.isBlank()) {
            draft.name = request.name;
        }
        if (request.prompt != null) {
            draft.prompt = request.prompt;
        }
        if (request.temperature >= 0.0 && request.temperature <= 2.0) {
            draft.temperature = request.temperature;
        }
        if (request.style != null) {
            draft.style = request.style;
        }
        if (request.targetPlatform != null) {
            draft.targetPlatform = request.targetPlatform;
        }
        draft.persist();

        // Replace news associations: delete old, insert new
        if (request.newsIds != null && !request.newsIds.isEmpty()) {
            DraftNews.delete("draftId", draft.id);
            saveDraftNewsAssociations(draft.id, request.newsIds);
        }

        return toDraftResponse(draft);
    }

    // ──────────────────────────────────────────────
    // CRUD: Delete
    // ──────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        Draft draft = Draft.findById(id);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }

        // Delete associated DraftVersion, DraftNews, then the Draft itself
        DraftVersion.delete("draft", draft);
        DraftNews.delete("draftId", draft.id);
        draft.delete();
    }

    // ──────────────────────────────────────────────
    // CRUD: Get by ID (with news)
    // ──────────────────────────────────────────────

    public DraftResponse getById(Long id) {
        Draft draft = Draft.findById(id);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }
        return toDraftResponse(draft);
    }

    // ──────────────────────────────────────────────
    // CRUD: List with pagination
    // ──────────────────────────────────────────────

    public PaginatedResponse<DraftListSummary> list(int page, int size) {
        PanacheQuery<Draft> query = Draft.findAll(Sort.by("updatedAt").descending());
        long total = query.count();
        List<Draft> drafts = query.page(Page.of(page, size)).list();

        List<DraftListSummary> items = drafts.stream()
            .map(this::toListSummary)
            .collect(Collectors.toList());

        return new PaginatedResponse<>(total, page, size, items);
    }

    // ──────────────────────────────────────────────
    // AI Generation
    // ──────────────────────────────────────────────

    @Transactional
    public GenerateResponse generate(Long draftId) {
        Draft draft = Draft.findById(draftId);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }

        // Load associated news
        List<DraftNews> associations = DraftNews.find("draftId", draftId).list();
        if (associations.isEmpty()) {
            throw new WebApplicationException("稿件未关联任何新闻素材", 400);
        }

        List<News> newsList = new ArrayList<>();
        for (DraftNews dn : associations) {
            News news = News.findById(dn.newsId);
            if (news != null) {
                newsList.add(news);
            }
        }

        // Build prompt
        String aiContent = buildDraftPrompt(draft, newsList);

        // Call AI via AiService
        String generatedContent;
        try {
            generatedContent = callAiDraftGeneration(draft, aiContent);
        } catch (Exception e) {
            throw new WebApplicationException(
                Response.status(502)
                    .entity(Map.of("message", "AI 服务不可用: " + e.getMessage()))
                    .build()
            );
        }

        if (generatedContent == null || generatedContent.isBlank()) {
            throw new WebApplicationException(
                Response.status(502)
                    .entity(Map.of("message", "AI 服务返回空内容"))
                    .build()
            );
        }

        // Calculate next version
        int newVersion = draft.latestVersion + 1;

        // Save DraftVersion
        DraftVersion version = new DraftVersion();
        version.draft = draft;
        version.version = newVersion;
        version.content = generatedContent;
        version.persist();

        // Update Draft
        draft.latestVersion = newVersion;
        draft.latestContent = generatedContent;
        draft.persist();

        return new GenerateResponse(generatedContent, newVersion);
    }

    // ──────────────────────────────────────────────
    // Material Pile Management
    // ──────────────────────────────────────────────

    public PaginatedResponse<MaterialPileItem> getMaterialPile(int page, int size) {
        PanacheQuery<News> query = News.find(
            "inMaterialPile = true",
            Sort.by("materialPileAddedAt").descending()
        );

        long total = query.count();
        List<News> newsList = query.page(Page.of(page, size)).list();

        List<MaterialPileItem> items = newsList.stream()
            .map(n -> new MaterialPileItem(n.id, n.title, n.materialPileAddedAt))
            .collect(Collectors.toList());

        return new PaginatedResponse<>(total, page, size, items);
    }

    @Transactional
    public void addToMaterialPile(Long newsId) {
        News news = News.findById(newsId);
        if (news == null) {
            throw new NotFoundException("新闻不存在");
        }
        if (!news.inMaterialPile) {
            news.inMaterialPile = true;
            news.materialPileAddedAt = LocalDateTime.now();
            news.persist();
        }
    }

    @Transactional
    public void removeFromMaterialPile(Long newsId) {
        News news = News.findById(newsId);
        if (news == null) {
            throw new NotFoundException("新闻不存在");
        }
        if (news.inMaterialPile) {
            news.inMaterialPile = false;
            news.materialPileAddedAt = null;
            news.persist();
        }
    }

    // ──────────────────────────────────────────────
    // Draft Versions
    // ──────────────────────────────────────────────

    public List<DraftVersionInfo> getVersions(Long draftId) {
        Draft draft = Draft.findById(draftId);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }

        List<DraftVersion> versions = DraftVersion.find(
            "draft = ?1 order by version desc", draft
        ).list();

        return versions.stream()
            .map(v -> new DraftVersionInfo(v.version, v.content, v.createdAt))
            .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private void saveDraftNewsAssociations(Long draftId, List<Long> newsIds) {
        int sortOrder = 0;
        for (Long newsId : newsIds) {
            DraftNews dn = new DraftNews();
            dn.draftId = draftId;
            dn.newsId = newsId;
            dn.sortOrder = sortOrder++;
            dn.persist();
        }
    }

    private DraftResponse toDraftResponse(Draft draft) {
        List<DraftNews> associations = DraftNews.find("draftId", draft.id).list();
        List<NewsBrief> newsBriefs = new ArrayList<>();

        for (DraftNews dn : associations) {
            News news = News.findById(dn.newsId);
            if (news != null) {
                newsBriefs.add(new NewsBrief(
                    news.id, news.title, news.summary, news.sourceRssName
                ));
            }
        }

        return new DraftResponse(
            draft.id,
            draft.name,
            draft.prompt,
            draft.temperature,
            draft.style,
            draft.targetPlatform,
            draft.latestVersion,
            draft.latestContent,
            draft.createdAt,
            draft.updatedAt,
            newsBriefs
        );
    }

    private DraftListSummary toListSummary(Draft draft) {
        long newsCount = DraftNews.count("draftId", draft.id);
        return new DraftListSummary(
            draft.id,
            draft.name,
            draft.style,
            draft.targetPlatform,
            (int) newsCount,
            draft.latestVersion,
            draft.createdAt,
            draft.updatedAt
        );
    }

    /**
     * Testable hook for AI draft generation. Delegates to AiService.
     */
    String callAiDraftGeneration(Draft draft, String prompt) {
        return aiService.generateDraft(prompt, draft.temperature);
    }

    /**
     * Build the prompt for AI draft generation by concatenating news content.
     */
    private String buildDraftPrompt(Draft draft, List<News> newsList) {
        StringBuilder sb = new StringBuilder();

        String style = draft.style != null ? draft.style : "正式";
        String platform = draft.targetPlatform != null ? draft.targetPlatform : "通用";

        // System / instruction portion
        sb.append("你是一个").append(style).append("风格的专业内容编辑。");
        sb.append("目标发布平台是").append(platform).append("。\n\n");

        // User prompt
        if (draft.prompt != null && !draft.prompt.isBlank()) {
            sb.append(draft.prompt).append("\n\n");
        }

        sb.append("参考素材：\n");

        int index = 1;
        for (News news : newsList) {
            sb.append("---\n");
            sb.append("新闻").append(index).append("：").append(news.title).append("\n");

            String plainText = extractPlainTextFromStructuredContent(news.structuredContent);
            if (plainText != null && !plainText.isBlank()) {
                sb.append(plainText).append("\n");
            } else if (news.summary != null && !news.summary.isBlank()) {
                sb.append(news.summary).append("\n");
            }

            index++;
        }

        return sb.toString();
    }

    /**
     * Extract plain text from cleaned HTML content (structured_content).
     * Strips all HTML tags, returning only the visible text.
     */
    private String extractPlainTextFromStructuredContent(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return null;
        }
        return Jsoup.parse(htmlContent).text();
    }

}
