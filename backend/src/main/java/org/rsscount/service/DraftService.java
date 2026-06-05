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
 * 稿件管理服务 — 管理 AI 辅助稿件生成的全流程。
 * 支持创建/编辑稿件、关联新闻素材、AI 生成、版本管理和素材堆管理。
 */
@ApplicationScoped
public class DraftService {

    @Inject
    AiService aiService;

    @Inject
    DraftService self;

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
        String targetPlatform,
        String latestContent
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

    /** 稿件版本信息 */
    public record DraftVersionInfo(
        int version,
        String content,
        LocalDateTime createdAt
    ) {}

    /** AI 生成响应 */
    public record GenerateResponse(
        String content,
        int version
    ) {}

    /** 素材堆中的新闻项 */
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

        draft.updatedAt = LocalDateTime.now();

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

        // Save a new version when content is provided and has changed
        if (request.latestContent != null && !request.latestContent.isBlank()) {
            int newVersion = draft.latestVersion + 1;

            // Deduplicate: skip if content is identical to latest version
            DraftVersion lastVer = DraftVersion.find("draft.id = ?1 order by version desc", id).firstResult();
            if (lastVer == null || !lastVer.content.equals(request.latestContent)) {
                DraftVersion dv = new DraftVersion();
                dv.draft = draft;
                dv.version = newVersion;
                dv.content = request.latestContent;
                dv.prompt = draft.prompt != null ? draft.prompt : "";
                dv.temperature = draft.temperature;
                dv.style = draft.style != null ? draft.style : "";
                dv.targetPlatform = draft.targetPlatform != null ? draft.targetPlatform : "";
                dv.createdAt = LocalDateTime.now();
                dv.persist();

                draft.latestVersion = newVersion;
                draft.latestContent = request.latestContent;
            }
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

    /**
     * 根据 ID 获取稿件详情（含关联的新闻素材列表）。
     * @param id 稿件 ID
     * @return 稿件响应
     * @throws NotFoundException 稿件不存在时抛出
     */
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

    /**
     * 分页查询稿件列表（按更新时间降序）。
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @return 分页的稿件摘要列表
     */
    public PaginatedResponse<DraftListSummary> list(int page, int size) {
        PanacheQuery<Draft> query = Draft.findAll(Sort.by("updatedAt").descending());
        long total = query.count();
        List<Draft> drafts = query.page(Page.of(page - 1, size)).list();

        List<DraftListSummary> items = drafts.stream()
            .map(this::toListSummary)
            .collect(Collectors.toList());

        return new PaginatedResponse<>(total, page, size, items);
    }

    // ──────────────────────────────────────────────
    // AI Generation
    // ──────────────────────────────────────────────

    /**
     * 调用 AI 生成稿件内容。
     * 流程：加载关联新闻 → 拼接提示词 → 调用 AI → 保存版本 → 更新稿件最新内容。
     * @param draftId 稿件 ID
     * @return 生成的稿件内容和版本号
     * @throws WebApplicationException AI 服务不可用或返回空内容时抛出（502）
     */
    public GenerateResponse generate(Long draftId) {
        Draft draft = Draft.findById(draftId);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }

        // Load associated news (non-transactional read)
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

        // Call AI via AiService (outside any transaction -- this is a 120s HTTP call)
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

        // Persist result in a new transaction (goes through the CDI proxy)
        return self.saveGenerationResult(draftId, generatedContent);
    }

    @Transactional
    public GenerateResponse saveGenerationResult(Long draftId, String generatedContent) {
        Draft draft = Draft.findById(draftId);
        if (draft == null) {
            throw new NotFoundException("稿件不存在");
        }

        int newVersion = draft.latestVersion + 1;

        DraftVersion version = new DraftVersion();
        version.draft = draft;
        version.version = newVersion;
        version.content = generatedContent;
        version.persist();

        draft.latestVersion = newVersion;
        draft.latestContent = generatedContent;
        draft.persist();

        return new GenerateResponse(generatedContent, newVersion);
    }

    // ──────────────────────────────────────────────
    // Material Pile Management
    // ──────────────────────────────────────────────

    /**
     * 分页查询素材堆中的新闻列表。
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @return 分页的素材堆项列表
     */
    public PaginatedResponse<MaterialPileItem> getMaterialPile(int page, int size) {
        PanacheQuery<News> query = News.find(
            "inMaterialPile = true",
            Sort.by("materialPileAddedAt").descending()
        );

        long total = query.count();
        List<News> newsList = query.page(Page.of(page - 1, size)).list();

        List<MaterialPileItem> items = newsList.stream()
            .map(n -> new MaterialPileItem(n.id, n.title, n.materialPileAddedAt))
            .collect(Collectors.toList());

        return new PaginatedResponse<>(total, page, size, items);
    }

    /**
     * 将新闻添加到素材堆。
     * @param newsId 新闻 ID
     * @throws NotFoundException 新闻不存在时抛出
     */
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

    /**
     * 将新闻从素材堆移除。
     * @param newsId 新闻 ID
     * @throws NotFoundException 新闻不存在时抛出
     */
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

    /**
     * 获取稿件的所有历史版本（按版本号降序）。
     * @param draftId 稿件 ID
     * @return 版本信息列表
     * @throws NotFoundException 稿件不存在时抛出
     */
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

        // Batch load all associated news in one query to avoid N+1
        List<Long> newsIds = associations.stream().map(dn -> dn.newsId).collect(Collectors.toList());
        Map<Long, News> newsMap = new HashMap<>();
        if (!newsIds.isEmpty()) {
            List<News> newsList = News.list("id in ?1", newsIds);
            for (News n : newsList) {
                newsMap.put(n.id, n);
            }
        }

        for (DraftNews dn : associations) {
            News news = newsMap.get(dn.newsId);
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
