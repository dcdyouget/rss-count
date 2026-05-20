package org.rsscount.controller;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rsscount.entity.*;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * I11: ReportController integration tests.
 *
 * I11-1: GET /reports returns paginated list.
 * I11-2: GET /reports/{id} returns detail with news.
 * I11-3: GET /reports/{id}/news/{newsId} returns news detail.
 */
@QuarkusTest
class ReportControllerTest {

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    @Transactional
    void cleanup() {
        NewsTag.deleteAll();
        Tag.deleteAll();
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    // ── I11-1: GET /reports ────────────────────────────────

    @Test
    void testListReports() throws Exception {
        runInTransaction(() -> {
            for (int i = 0; i < 3; i++) {
                Task task = new Task();
                task.name = "报告任务" + (i + 1);
                task.timeRangeStart = LocalDateTime.now().minusDays(1);
                task.timeRangeEnd = LocalDateTime.now();
                task.status = Task.STATUS_COMPLETED;
                task.sourceType = Task.SOURCE_ALL;
                task.createdAt = LocalDateTime.now().minusHours(i);
                task.startedAt = LocalDateTime.now().minusHours(i);
                task.endedAt = LocalDateTime.now().minusHours(i - 1);
                task.persist();

                Report report = new Report();
                report.task = task;
                report.name = "测试报告" + (i + 1);
                report.timeRangeStart = task.timeRangeStart;
                report.timeRangeEnd = task.timeRangeEnd;
                report.newsCount = 5;
                report.createdAt = task.createdAt;
                report.persist();
            }
        });

        given()
            .when()
            .queryParam("page", 1)
            .queryParam("size", 20)
            .get("/api/v1/reports")
            .then()
            .statusCode(200)
            .body("total", equalTo(3))
            .body("items.size()", equalTo(3))
            .body("items[0].name", notNullValue())
            .body("items[0].newsCount", notNullValue());
    }

    @Test
    void testListReportsEmpty() {
        given()
            .when()
            .get("/api/v1/reports")
            .then()
            .statusCode(200)
            .body("total", equalTo(0))
            .body("items.size()", equalTo(0));
    }

    // ── I11-2: GET /reports/{id} ───────────────────────────

    @Test
    void testGetReportDetail() throws Exception {
        runInTransaction(() -> {
            Task task = new Task();
            task.name = "详情任务";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.startedAt = LocalDateTime.now().minusHours(1);
            task.endedAt = LocalDateTime.now();
            task.persist();

            Report report = new Report();
            report.task = task;
            report.name = "详细报告";
            report.timeRangeStart = task.timeRangeStart;
            report.timeRangeEnd = task.timeRangeEnd;
            report.newsCount = 2;
            report.persist();

            for (int i = 0; i < 2; i++) {
                News news = new News();
                news.report = report;
                news.title = "报告新闻" + (i + 1);
                news.summary = "摘要" + (i + 1);
                news.author = "作者";
                news.sourceRssName = "源";
                news.sourceUrl = "https://example.com/" + i;
                news.createdAt = LocalDateTime.now();
                news.persist();
            }
        });

        given()
            .when()
            .get("/api/v1/reports/1")
            .then()
            .statusCode(200)
            .body("name", equalTo("详细报告"))
            .body("newsCount", equalTo(2))
            .body("news.size()", equalTo(2));
    }

    @Test
    void testGetReportDetailNotFound() {
        given()
            .when()
            .get("/api/v1/reports/99999")
            .then()
            .statusCode(404);
    }

    // ── I11-3: GET /reports/{id}/news/{newsId} ─────────────

    @Test
    void testGetNewsDetail() throws Exception {
        runInTransaction(() -> {
            Task task = new Task();
            task.name = "新闻详情任务";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.startedAt = LocalDateTime.now().minusHours(1);
            task.endedAt = LocalDateTime.now();
            task.persist();

            Report report = new Report();
            report.task = task;
            report.name = "新闻详情报告";
            report.timeRangeStart = task.timeRangeStart;
            report.timeRangeEnd = task.timeRangeEnd;
            report.persist();

            News news = new News();
            news.report = report;
            news.title = "报告内新闻";
            news.author = "张作者";
            news.sourceRssName = "新闻源";
            news.sourceUrl = "https://example.com/article";
            news.summary = "新闻摘要内容";
            news.structuredContent = "[{\"type\":\"paragraph\",\"text\":\"正文\"}]";
            news.isRead = false;
            news.inMaterialPile = false;
            news.createdAt = LocalDateTime.now();
            news.persist();
        });

        given()
            .when()
            .get("/api/v1/reports/1/news/1")
            .then()
            .statusCode(200)
            .body("title", equalTo("报告内新闻"))
            .body("author", equalTo("张作者"))
            .body("sourceRssName", equalTo("新闻源"))
            .body("tags", notNullValue());
    }

    @Test
    void testGetNewsDetailWrongReport() throws Exception {
        runInTransaction(() -> {
            Task task = new Task();
            task.name = "任务A";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.persist();

            Report reportA = new Report();
            reportA.task = task;
            reportA.name = "报告A";
            reportA.timeRangeStart = task.timeRangeStart;
            reportA.timeRangeEnd = task.timeRangeEnd;
            reportA.persist();

            News news = new News();
            news.report = reportA;
            news.title = "属于报告A的新闻";
            news.author = "作者";
            news.sourceRssName = "源";
            news.sourceUrl = "https://example.com";
            news.createdAt = LocalDateTime.now();
            news.persist();
        });

        // Try to access news with wrong report ID
        given()
            .when()
            .get("/api/v1/reports/999/news/1")
            .then()
            .statusCode(404);
    }

    @Test
    void testGetNewsDetailNotFound() {
        given()
            .when()
            .get("/api/v1/reports/1/news/99999")
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
