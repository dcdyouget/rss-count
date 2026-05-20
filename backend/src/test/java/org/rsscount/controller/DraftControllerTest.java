package org.rsscount.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rsscount.entity.*;
import org.rsscount.service.AiService;
import org.rsscount.service.DraftService;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * I12: DraftController integration tests.
 *
 * I12-1: POST /drafts creates draft.
 * I12-2: GET /drafts returns list.
 * I12-3: GET /drafts/{id} returns detail.
 * I12-4: PUT /drafts/{id} updates draft.
 * I12-5: DELETE /drafts/{id} deletes draft.
 * I12-6: POST /drafts/{id}/generate generates content.
 */
@QuarkusTest
class DraftControllerTest {

    @Inject
    UserTransaction userTransaction;

    @InjectMock
    AiService mockAiService;

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

    private Long createTestNews() {
        try {
            userTransaction.begin();

            Task task = new Task();
            task.name = "稿件测试任务";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.persist();

            Report report = new Report();
            report.task = task;
            report.name = "稿件测试报告";
            report.timeRangeStart = task.timeRangeStart;
            report.timeRangeEnd = task.timeRangeEnd;
            report.persist();

            News news = new News();
            news.report = report;
            news.title = "稿件关联新闻";
            news.summary = "新闻摘要";
            news.author = "作者";
            news.sourceRssName = "稿件源";
            news.sourceUrl = "https://example.com/news";
            news.createdAt = LocalDateTime.now();
            news.persist();

            userTransaction.commit();
            return news.id;
        } catch (Exception e) {
            try { userTransaction.rollback(); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }

    // ── I12-1: POST /drafts ────────────────────────────────

    @Test
    void testCreateDraft() {
        Long newsId = createTestNews();

        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "API创建稿件",
                    "newsIds": [%d],
                    "prompt": "请写一篇分析",
                    "temperature": 0.7,
                    "style": "正式",
                    "targetPlatform": "知乎"
                }
                """.formatted(newsId))
            .when()
            .post("/api/v1/drafts")
            .then()
            .statusCode(201)
            .body("name", equalTo("API创建稿件"))
            .body("news.size()", equalTo(1));
    }

    @Test
    void testCreateDraftWithEmptyNameFails() {
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "",
                    "newsIds": [1],
                    "temperature": 0.7
                }
                """)
            .when()
            .post("/api/v1/drafts")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateDraftWithNoNewsFails() {
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "空新闻稿件",
                    "newsIds": [],
                    "temperature": 0.7
                }
                """)
            .when()
            .post("/api/v1/drafts")
            .then()
            .statusCode(400);
    }

    // ── I12-2: GET /drafts ─────────────────────────────────

    @Test
    void testListDrafts() throws Exception {
        Long newsId = createTestNews();

        runInTransaction(() -> {
            for (int i = 0; i < 3; i++) {
                Draft draft = new Draft();
                draft.name = "列表稿件" + (i + 1);
                draft.prompt = "prompt" + (i + 1);
                draft.temperature = 0.5;
                draft.style = "正式";
                draft.targetPlatform = "知乎";
                draft.persist();
            }
        });

        given()
            .when()
            .queryParam("page", 1)
            .queryParam("size", 20)
            .get("/api/v1/drafts")
            .then()
            .statusCode(200)
            .body("total", equalTo(3))
            .body("items.size()", equalTo(3))
            .body("items[0].name", notNullValue());
    }

    // ── I12-3: GET /drafts/{id} ────────────────────────────

    @Test
    void testGetDraftDetail() throws Exception {
        Long newsId = createTestNews();

        runInTransaction(() -> {
            Draft draft = new Draft();
            draft.name = "详情稿件";
            draft.prompt = "详情prompt";
            draft.temperature = 0.5;
            draft.style = "口语化";
            draft.targetPlatform = "今日头条";
            draft.persist();

            DraftNews dn = new DraftNews();
            dn.draftId = draft.id;
            dn.newsId = newsId;
            dn.sortOrder = 0;
            dn.persist();
        });

        given()
            .when()
            .get("/api/v1/drafts/1")
            .then()
            .statusCode(200)
            .body("name", equalTo("详情稿件"))
            .body("news.size()", equalTo(1));
    }

    @Test
    void testGetDraftDetailNotFound() {
        given()
            .when()
            .get("/api/v1/drafts/99999")
            .then()
            .statusCode(404);
    }

    // ── I12-4: PUT /drafts/{id} ────────────────────────────

    @Test
    void testUpdateDraft() {
        Long newsId = createTestNews();
        Long anotherNewsId = createTestNews();

        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "创建更新稿件",
                    "newsIds": [%d],
                    "prompt": "原始prompt",
                    "temperature": 0.3,
                    "style": "口语化",
                    "targetPlatform": "微博"
                }
                """.formatted(newsId))
            .when()
            .post("/api/v1/drafts")
            .then()
            .statusCode(201);

        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "已更新稿件",
                    "newsIds": [%d],
                    "prompt": "更新后的prompt",
                    "temperature": 0.9,
                    "style": "正式",
                    "targetPlatform": "知乎"
                }
                """.formatted(anotherNewsId))
            .when()
            .put("/api/v1/drafts/1")
            .then()
            .statusCode(200)
            .body("name", equalTo("已更新稿件"))
            .body("prompt", equalTo("更新后的prompt"));
    }

    // ── I12-5: DELETE /drafts/{id} ─────────────────────────

    @Test
    void testDeleteDraft() {
        Long newsId = createTestNews();

        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "待删除稿件",
                    "newsIds": [%d],
                    "prompt": "prompt",
                    "temperature": 0.7,
                    "style": "正式",
                    "targetPlatform": "知乎"
                }
                """.formatted(newsId))
            .when()
            .post("/api/v1/drafts")
            .then()
            .statusCode(201);

        given()
            .when()
            .delete("/api/v1/drafts/1")
            .then()
            .statusCode(204);
    }

    @Test
    void testDeleteDraftNotFound() {
        given()
            .when()
            .delete("/api/v1/drafts/99999")
            .then()
            .statusCode(404);
    }

    // ── I12-6: POST /drafts/{id}/generate ──────────────────

    @Test
    void testGenerateDraft() {
        Long newsId = createTestNews();

        // Create draft via API
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "生成稿件",
                    "newsIds": [%d],
                    "prompt": "请写一篇分析",
                    "temperature": 0.7,
                    "style": "正式",
                    "targetPlatform": "知乎"
                }
                """.formatted(newsId))
            .when()
            .post("/api/v1/drafts")
            .then()
            .statusCode(201);

        // Mock AI service
        Mockito.when(mockAiService.generateDraft(anyString(), anyDouble()))
            .thenReturn("这是AI生成的内容。");

        given()
            .when()
            .post("/api/v1/drafts/1/generate")
            .then()
            .statusCode(200)
            .body("content", notNullValue())
            .body("version", equalTo(1));
    }

    @Test
    void testGenerateDraftNotFound() {
        given()
            .when()
            .post("/api/v1/drafts/99999/generate")
            .then()
            .statusCode(404);
    }

    // ── Helpers ────────────────────────────────────────────

    private void runInTransaction(Runnable action) {
        try {
            userTransaction.begin();
            action.run();
            userTransaction.commit();
        } catch (Exception e) {
            try { userTransaction.rollback(); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }
}
