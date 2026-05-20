package org.rsscount.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rsscount.entity.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * I6: Draft service integration tests.
 *
 * I6-1: Create draft with associated news.
 * I6-2: AI generation — verify DraftVersion created and version incremented.
 * I6-3: Version record increment across multiple generations.
 * I6-4: Material pile add/remove.
 */
@QuarkusTest
class DraftServiceTest {

    @InjectMock
    AiService aiService;

    @Inject
    DraftService draftService;

    @BeforeEach
    @Transactional
    void cleanup() {
        DraftVersion.deleteAll();
        DraftNews.deleteAll();
        Draft.deleteAll();
        NewsTag.deleteAll();
        Tag.deleteAll();
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    // ── Helpers ──

    @Transactional
    News createTestNews(String title) {
        Task task = new Task();
        task.name = "Test Task for " + title;
        task.timeRangeStart = LocalDateTime.now().minusHours(2);
        task.timeRangeEnd = LocalDateTime.now();
        task.status = Task.STATUS_COMPLETED;
        task.sourceType = Task.SOURCE_ALL;
        task.createdAt = LocalDateTime.now();
        task.persist();

        Report report = new Report();
        report.task = task;
        report.name = "Test Report for " + title;
        report.timeRangeStart = task.timeRangeStart;
        report.timeRangeEnd = task.timeRangeEnd;
        report.newsCount = 1;
        report.createdAt = LocalDateTime.now();
        report.persist();

        News news = new News();
        news.report = report;
        news.title = title;
        news.summary = title + "的摘要内容";
        news.author = "测试作者";
        news.sourceRssName = "测试源";
        news.sourceUrl = "https://example.com/news/" + title.replace(" ", "_");
        news.isRead = false;
        news.inMaterialPile = false;
        news.publishedAt = LocalDateTime.now().minusHours(1);
        news.contentLength = 100;
        news.structuredContent = """
            [{"type":"heading","level":2,"text":"%s 详细内容"},{"type":"paragraph","text":"这是%s的正文段落一。段落二。段落三。段落四。段落五。段落六。段落七。段落八。段落九。段落十。"}]
            """.formatted(title, title);
        news.createdAt = LocalDateTime.now();
        news.persist();

        return news;
    }

    // ═══════════════════════════════════════════════
    // I6-1: Create draft with associated news
    // ═══════════════════════════════════════════════

    @Test
    @Transactional
    void testCreateDraft() {
        News news1 = createTestNews("新闻A");
        News news2 = createTestNews("新闻B");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "测试稿件",
            List.of(news1.id, news2.id),
            "请写一篇分析文章",
            0.7,
            "正式",
            "知乎"
        );

        DraftService.DraftResponse response = draftService.create(req);

        assertNotNull(response);
        assertEquals("测试稿件", response.name());
        assertEquals("请写一篇分析文章", response.prompt());
        assertEquals(0.7, response.temperature());
        assertEquals("正式", response.style());
        assertEquals("知乎", response.targetPlatform());
        assertEquals(0, response.latestVersion());
        assertNull(response.latestContent());
        assertEquals(2, response.news().size());

        List<String> newsTitles = response.news().stream()
            .map(DraftService.NewsBrief::title)
            .toList();
        assertTrue(newsTitles.contains("新闻A"));
        assertTrue(newsTitles.contains("新闻B"));

        // Verify DraftNews associations in DB
        long count = DraftNews.count("draftId", response.id());
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testCreateDraftWithEmptyNameFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            draftService.create(new DraftService.CreateDraftRequest(
                "", List.of(1L), "prompt", 0.7, "正式", "知乎"
            ));
        });
    }

    @Test
    @Transactional
    void testCreateDraftWithEmptyNewsIdsFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            draftService.create(new DraftService.CreateDraftRequest(
                "稿件", List.of(), "prompt", 0.7, "正式", "知乎"
            ));
        });
    }

    @Test
    @Transactional
    void testCreateDraftWithInvalidTemperatureFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            draftService.create(new DraftService.CreateDraftRequest(
                "稿件", List.of(1L), "prompt", 3.0, "正式", "知乎"
            ));
        });
    }

    // ═══════════════════════════════════════════════
    // I6-2: AI generate draft content (mock AiService)
    // ═══════════════════════════════════════════════

    @Test
    @Transactional
    void testGenerateDraftCallsAiAndCreatesVersion() {
        News news = createTestNews("AI新闻");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "AI生成稿件",
            List.of(news.id),
            "请写一篇分析",
            0.7,
            "正式",
            "知乎"
        );
        DraftService.DraftResponse draft = draftService.create(req);

        // Mock the AI service to return generated content
        String generatedContent = "这是AI生成的长篇稿件内容，包含对新闻的深入分析...";
        Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
            .thenReturn(generatedContent);

        DraftService.GenerateResponse genResponse = draftService.generate(draft.id());

        assertNotNull(genResponse);
        assertEquals(generatedContent, genResponse.content());
        assertEquals(1, genResponse.version());

        // Verify DraftVersion was saved
        long versionCount = DraftVersion.count();
        assertEquals(1, versionCount);

        // Verify Draft was updated
        Draft updatedDraft = Draft.findById(draft.id());
        assertNotNull(updatedDraft);
        assertEquals(1, updatedDraft.latestVersion);
        assertEquals(generatedContent, updatedDraft.latestContent);
    }

    @Test
    @Transactional
    void testGenerateDraftWithNoNewsFails() {
        Draft draft = new Draft();
        draft.name = "无新闻稿件";
        draft.prompt = "test prompt";
        draft.temperature = 0.7;
        draft.style = "正式";
        draft.targetPlatform = "知乎";
        draft.persist();

        try {
            draftService.generate(draft.id);
            fail("Expected exception for draft with no news");
        } catch (jakarta.ws.rs.WebApplicationException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }

    @Test
    @Transactional
    void testGenerateDraftNotFound() {
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            draftService.generate(99999L);
        });
    }

    @Test
    @Transactional
    void testGenerateDraftAiUnavailableReturns502() {
        News news = createTestNews("AI故障新闻");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "AI故障稿件",
            List.of(news.id),
            "prompt",
            0.7,
            "正式",
            "知乎"
        );
        DraftService.DraftResponse draft = draftService.create(req);

        // Mock AI service to throw an exception
        Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
            .thenThrow(new RuntimeException("AI 连接超时"));

        try {
            draftService.generate(draft.id());
            fail("Expected 502 exception");
        } catch (jakarta.ws.rs.WebApplicationException e) {
            assertEquals(502, e.getResponse().getStatus());
        }
    }

    // ═══════════════════════════════════════════════
    // I6-3: Version record increment across generations
    // ═══════════════════════════════════════════════

    @Test
    @Transactional
    void testVersionIncrementsAcrossMultipleGenerations() {
        News news = createTestNews("版本测试新闻");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "多版本稿件",
            List.of(news.id),
            "请分析",
            0.5,
            "口语化",
            "今日头条"
        );
        DraftService.DraftResponse draft = draftService.create(req);

        // First generation
        Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
            .thenReturn("第一版内容：初步分析");
        DraftService.GenerateResponse gen1 = draftService.generate(draft.id());
        assertEquals(1, gen1.version());
        assertEquals("第一版内容：初步分析", gen1.content());

        // Second generation
        Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
            .thenReturn("第二版内容：深入分析，增加了更多观点");
        DraftService.GenerateResponse gen2 = draftService.generate(draft.id());
        assertEquals(2, gen2.version());
        assertEquals("第二版内容：深入分析，增加了更多观点", gen2.content());

        // Third generation
        Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
            .thenReturn("第三版内容：最终定稿，结构完善");
        DraftService.GenerateResponse gen3 = draftService.generate(draft.id());
        assertEquals(3, gen3.version());
        assertEquals("第三版内容：最终定稿，结构完善", gen3.content());

        // Verify 3 draft versions in DB
        List<DraftVersion> versions = DraftVersion.list("draft",
            Draft.<Draft>findById(draft.id()));
        assertEquals(3, versions.size());

        // Verify Draft has latest version info
        Draft updatedDraft = Draft.findById(draft.id());
        assertEquals(3, updatedDraft.latestVersion);
        assertEquals("第三版内容：最终定稿，结构完善", updatedDraft.latestContent);
    }

    @Test
    @Transactional
    void testGetVersionsReturnsAllVersionsDescending() {
        News news = createTestNews("版本历史新闻");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "版本历史稿件",
            List.of(news.id),
            "prompt",
            0.7,
            "正式",
            "通用"
        );
        DraftService.DraftResponse draft = draftService.create(req);

        // Generate 3 versions
        String[] contents = {"版本1的内容", "版本2的内容", "版本3的内容"};
        for (int i = 0; i < 3; i++) {
            Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
                .thenReturn(contents[i]);
            draftService.generate(draft.id());
        }

        List<DraftService.DraftVersionInfo> versions = draftService.getVersions(draft.id());
        assertEquals(3, versions.size());
        // Should be descending (latest first)
        assertEquals(3, versions.get(0).version());
        assertEquals("版本3的内容", versions.get(0).content());
        assertEquals(2, versions.get(1).version());
        assertEquals(1, versions.get(2).version());
    }

    // ═══════════════════════════════════════════════
    // I6-4: Material pile add/remove
    // ═══════════════════════════════════════════════

    @Test
    @Transactional
    void testAddToMaterialPile() {
        News news = createTestNews("素材候选新闻");
        assertFalse(news.inMaterialPile);

        draftService.addToMaterialPile(news.id);

        News updated = News.findById(news.id);
        assertTrue(updated.inMaterialPile);
        assertNotNull(updated.materialPileAddedAt);
    }

    @Test
    @Transactional
    void testRemoveFromMaterialPile() {
        News news = createTestNews("待移除新闻");
        news.inMaterialPile = true;
        news.materialPileAddedAt = LocalDateTime.now();
        news.persist();

        draftService.removeFromMaterialPile(news.id);

        News updated = News.findById(news.id);
        assertFalse(updated.inMaterialPile);
        assertNull(updated.materialPileAddedAt);
    }

    @Test
    @Transactional
    void testAddToMaterialPileIdempotent() {
        News news = createTestNews("幂等素材新闻");
        news.inMaterialPile = true;
        news.materialPileAddedAt = LocalDateTime.now();
        news.persist();

        LocalDateTime originalAddedAt = news.materialPileAddedAt;

        // Adding again should be a no-op (no exception)
        draftService.addToMaterialPile(news.id);

        News updated = News.findById(news.id);
        assertTrue(updated.inMaterialPile);
        assertEquals(originalAddedAt, updated.materialPileAddedAt);
    }

    @Test
    @Transactional
    void testGetMaterialPile() {
        News news1 = createTestNews("素材新闻1");
        news1.inMaterialPile = true;
        news1.materialPileAddedAt = LocalDateTime.now();
        news1.persist();

        News news2 = createTestNews("素材新闻2");
        news2.inMaterialPile = true;
        news2.materialPileAddedAt = LocalDateTime.now();
        news2.persist();

        createTestNews("非素材新闻"); // not in pile

        DraftService.PaginatedResponse<DraftService.MaterialPileItem> result =
            draftService.getMaterialPile(1, 20);

        assertEquals(2, result.total());
        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().anyMatch(i -> i.title().equals("素材新闻1")));
        assertTrue(result.items().stream().anyMatch(i -> i.title().equals("素材新闻2")));
    }

    @Test
    @Transactional
    void testRemoveFromMaterialPileWhenNotInPileIsNoOp() {
        News news = createTestNews("不在素材堆的新闻");
        assertFalse(news.inMaterialPile);

        // Should not throw
        draftService.removeFromMaterialPile(news.id);

        News updated = News.findById(news.id);
        assertFalse(updated.inMaterialPile);
    }

    @Test
    @Transactional
    void testAddToMaterialPileNotFound() {
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            draftService.addToMaterialPile(99999L);
        });
    }

    // ═══════════════════════════════════════════════
    // CRUD: Update, Delete, List
    // ═══════════════════════════════════════════════

    @Test
    @Transactional
    void testUpdateDraftReplacesNews() {
        News news1 = createTestNews("旧新闻A");
        News news2 = createTestNews("旧新闻B");
        News news3 = createTestNews("新新闻C");

        DraftService.CreateDraftRequest createReq = new DraftService.CreateDraftRequest(
            "可更新稿件",
            List.of(news1.id, news2.id),
            "原始prompt",
            0.3,
            "口语化",
            "微博"
        );
        DraftService.DraftResponse draft = draftService.create(createReq);
        assertEquals(2, draft.news().size());

        // Update: replace news list
        DraftService.UpdateDraftRequest updateReq = new DraftService.UpdateDraftRequest(
            "已更新稿件",
            List.of(news3.id),
            "新prompt",
            0.9,
            "正式",
            "知乎"
        );
        DraftService.DraftResponse updated = draftService.update(draft.id(), updateReq);

        assertEquals("已更新稿件", updated.name());
        assertEquals("新prompt", updated.prompt());
        assertEquals(0.9, updated.temperature());
        assertEquals("正式", updated.style());
        assertEquals("知乎", updated.targetPlatform());
        assertEquals(1, updated.news().size());
        assertEquals("新新闻C", updated.news().get(0).title());
    }

    @Test
    @Transactional
    void testDeleteDraftCascades() {
        News news = createTestNews("将被删除的稿件关联新闻");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "待删除稿件",
            List.of(news.id),
            "prompt",
            0.7,
            "正式",
            "知乎"
        );
        DraftService.DraftResponse draft = draftService.create(req);

        // Generate content to create a DraftVersion
        Mockito.when(aiService.generateDraft(anyString(), anyDouble()))
            .thenReturn("生成的内容");
        draftService.generate(draft.id());

        Long draftId = draft.id();

        // Verify associations exist
        assertTrue(DraftNews.count("draftId", draftId) > 0);

        // Delete
        draftService.delete(draftId);

        // Verify cascade delete
        assertNull(Draft.findById(draftId));
        assertEquals(0, DraftNews.count("draftId", draftId));
    }

    @Test
    @Transactional
    void testDeleteNonexistentFails() {
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            draftService.delete(99999L);
        });
    }

    @Test
    @Transactional
    void testListDraftsPaginated() {
        News news = createTestNews("列表测试新闻");

        for (int i = 1; i <= 5; i++) {
            DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
                "稿件" + i,
                List.of(news.id),
                "prompt" + i,
                0.5,
                "正式",
                "知乎"
            );
            draftService.create(req);
        }

        DraftService.PaginatedResponse<DraftService.DraftListSummary> page1 =
            draftService.list(1, 3);

        assertEquals(5, page1.total());
        assertEquals(3, page1.items().size());
        assertEquals(1, page1.page());

        DraftService.PaginatedResponse<DraftService.DraftListSummary> page2 =
            draftService.list(2, 3);

        assertEquals(2, page2.items().size());
        assertEquals(2, page2.page());
    }

    @Test
    @Transactional
    void testGetByIdReturnsNews() {
        News news1 = createTestNews("关联新闻1");
        News news2 = createTestNews("关联新闻2");

        DraftService.CreateDraftRequest req = new DraftService.CreateDraftRequest(
            "详情测试稿件",
            List.of(news1.id, news2.id),
            "prompt",
            0.7,
            "正式",
            "知乎"
        );
        DraftService.DraftResponse created = draftService.create(req);

        DraftService.DraftResponse fetched = draftService.getById(created.id());

        assertEquals(created.id(), fetched.id());
        assertEquals("详情测试稿件", fetched.name());
        assertEquals(2, fetched.news().size());
    }

    @Test
    @Transactional
    void testGetByIdNotFound() {
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            draftService.getById(99999L);
        });
    }
}
