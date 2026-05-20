package org.rsscount.controller;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rsscount.entity.News;
import org.rsscount.entity.NewsTag;
import org.rsscount.entity.Report;
import org.rsscount.entity.Tag;
import org.rsscount.entity.Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * News integration tests covering CRUD, search, read status, and material pile.
 */
@QuarkusTest
class NewsControllerTest {

    @BeforeEach
    @Transactional
    void cleanup() {
        NewsTag.deleteAll();
        Tag.deleteAll();
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    // ---- List tests ----

    @Test
    void testListNews() {
        QuarkusTransaction.requiringNew().run(() -> {
            createTestNews("新闻A", false, false);
            createTestNews("新闻B", false, false);
            createTestNews("新闻C", true, false);
        });

        given()
            .queryParam("page", 1)
            .queryParam("size", 20)
            .when()
            .get("/api/v1/news")
            .then()
            .statusCode(200)
            .body("total", equalTo(3))
            .body("items.size()", equalTo(3))
            .body("items[0].title", notNullValue());
    }

    @Test
    void testListNewsWithKeywordSearch() {
        QuarkusTransaction.requiringNew().run(() -> {
            createTestNews("人工智能突破", false, false);
            createTestNews("区块链发展", false, false);
            createTestNews("AI行业新闻", false, false);
        });

        given()
            .queryParam("keyword", "AI")
            .when()
            .get("/api/v1/news")
            .then()
            .statusCode(200)
            .body("total", equalTo(1))
            .body("items[0].title", equalTo("AI行业新闻"));
    }

    @Test
    void testListNewsFilterByReadStatus() {
        QuarkusTransaction.requiringNew().run(() -> {
            createTestNews("已读新闻", true, false);
            createTestNews("未读新闻1", false, false);
            createTestNews("未读新闻2", false, false);
        });

        given()
            .queryParam("isRead", false)
            .when()
            .get("/api/v1/news")
            .then()
            .statusCode(200)
            .body("total", equalTo(2));
    }

    @Test
    void testListNewsPagination() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 1; i <= 15; i++) {
                createTestNews("分页新闻" + i, false, false);
            }
        });

        given()
            .queryParam("page", 1)
            .queryParam("size", 10)
            .when()
            .get("/api/v1/news")
            .then()
            .statusCode(200)
            .body("total", equalTo(15))
            .body("items.size()", equalTo(10))
            .body("page", equalTo(1))
            .body("size", equalTo(10));
    }

    // ---- Detail test ----

    @Test
    void testGetNewsDetail() {
        long newsId = QuarkusTransaction.call(() -> {
            News news = createTestNews("详细新闻", false, false);
            return news.id;
        });

        given()
            .when()
            .get("/api/v1/news/" + newsId)
            .then()
            .statusCode(200)
            .body("id", equalTo((int) newsId))
            .body("title", equalTo("详细新闻"))
            .body("author", equalTo("测试作者"))
            .body("isRead", equalTo(false))
            .body("inMaterialPile", equalTo(false));
    }

    @Test
    void testGetNewsDetailNotFound() {
        given()
            .when()
            .get("/api/v1/news/99999")
            .then()
            .statusCode(404);
    }

    // ---- Mark as read test ----

    @Test
    void testMarkAsRead() {
        long newsId = QuarkusTransaction.call(() -> {
            News news = createTestNews("待读新闻", false, false);
            return news.id;
        });

        given()
            .when()
            .put("/api/v1/news/" + newsId + "/read")
            .then()
            .statusCode(200);

        given()
            .when()
            .get("/api/v1/news/" + newsId)
            .then()
            .statusCode(200)
            .body("isRead", equalTo(true));
    }

    // ---- Batch material pile tests ----

    @Test
    void testBatchAddToMaterialPile() {
        long[] ids = new long[2];
        QuarkusTransaction.requiringNew().run(() -> {
            News news1 = createTestNews("素材新闻1", false, false);
            News news2 = createTestNews("素材新闻2", false, false);
            ids[0] = news1.id;
            ids[1] = news2.id;
        });

        Map<String, Object> body = Map.of(
            "newsIds", List.of((int) ids[0], (int) ids[1]),
            "action", "ADD"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/news/batch-material-pile")
            .then()
            .statusCode(200)
            .body("affected", equalTo(2));

        given()
            .when()
            .get("/api/v1/news/" + ids[0])
            .then()
            .statusCode(200)
            .body("inMaterialPile", equalTo(true));
    }

    @Test
    @Transactional
    void testBatchRemoveFromMaterialPile() {
        News news = createTestNews("移除素材新闻", false, true);
        long newsId = news.id;

        Map<String, Object> body = Map.of(
            "newsIds", List.of((int) newsId),
            "action", "REMOVE"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/news/batch-material-pile")
            .then()
            .statusCode(200)
            .body("affected", equalTo(1));

        given()
            .when()
            .get("/api/v1/news/" + newsId)
            .then()
            .statusCode(200)
            .body("inMaterialPile", equalTo(false));
    }

    @Test
    @Transactional
    void testBatchMaterialPileIdempotent() {
        News news = createTestNews("幂等素材新闻", false, true);
        long newsId = news.id;

        Map<String, Object> body = Map.of(
            "newsIds", List.of((int) newsId),
            "action", "ADD"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/news/batch-material-pile")
            .then()
            .statusCode(200)
            .body("affected", equalTo(0));
    }

    // ---- Material pile list ----

    @Test
    void testMaterialPileList() {
        QuarkusTransaction.requiringNew().run(() -> {
            createTestNews("素材A", false, true);
            createTestNews("素材B", false, true);
            createTestNews("非素材", false, false);
        });

        given()
            .when()
            .get("/api/v1/news/material-pile")
            .then()
            .statusCode(200)
            .body("total", equalTo(2))
            .body("items.size()", equalTo(2));
    }

    // ---- Validation ----

    @Test
    void testBatchMaterialPileInvalidAction() {
        Map<String, Object> body = Map.of(
            "newsIds", List.of(1),
            "action", "INVALID"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/news/batch-material-pile")
            .then()
            .statusCode(400);
    }

    @Test
    void testBatchMaterialPileEmptyIds() {
        Map<String, Object> body = Map.of(
            "newsIds", List.of(),
            "action", "ADD"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/news/batch-material-pile")
            .then()
            .statusCode(400);
    }

    // ---- Helper ----

    private News createTestNews(String title, boolean isRead, boolean inMaterialPile) {
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
        news.summary = title + "的摘要";
        news.author = "测试作者";
        news.sourceRssName = "测试源";
        news.sourceUrl = "https://example.com/news";
        news.isRead = isRead;
        news.inMaterialPile = inMaterialPile;
        news.materialPileAddedAt = inMaterialPile ? LocalDateTime.now() : null;
        news.publishedAt = LocalDateTime.now().minusHours(1);
        news.contentLength = 100;
        news.createdAt = LocalDateTime.now();
        news.persist();

        return news;
    }
}
