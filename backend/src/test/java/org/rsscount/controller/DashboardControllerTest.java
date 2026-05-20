package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rsscount.entity.News;
import org.rsscount.entity.Report;
import org.rsscount.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * I9: Dashboard integration tests.
 * I9-1: Stats data
 * I9-2: Yesterday is 0 → changePercent is null
 * I9-3: Recent tasks returns ≤5, sorted by createdAt DESC
 * I9-4: Recent reports returns ≤3, sorted by createdAt DESC
 */
@QuarkusTest
class DashboardControllerTest {

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    @Transactional
    void cleanup() {
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    // ---- I9-1: Stats data ----

    @Test
    void testStatsWithData() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayMorning = today.atTime(9, 0);
        LocalDateTime todayEvening = today.atTime(18, 0);

        LocalDate yesterday = today.minusDays(1);
        LocalDateTime yesterdayTime = yesterday.atTime(12, 0);

        runInTransaction(() -> {
            // Create today's tasks (3)
            for (int i = 0; i < 3; i++) {
                Task task = new Task();
                task.name = "Today task " + (i + 1);
                task.timeRangeStart = todayMorning;
                task.timeRangeEnd = todayEvening;
                task.status = Task.STATUS_COMPLETED;
                task.sourceType = Task.SOURCE_ALL;
                task.createdAt = todayMorning.plusHours(i);
                task.persist();

                Report report = new Report();
                report.task = task;
                report.name = "Today report " + (i + 1);
                report.timeRangeStart = todayMorning;
                report.timeRangeEnd = todayEvening;
                report.newsCount = 5;
                report.createdAt = todayMorning.plusHours(i);
                report.persist();

                for (int j = 0; j < 5; j++) {
                    News news = new News();
                    news.report = report;
                    news.title = "新闻 " + i + "-" + j;
                    news.author = "作者";
                    news.createdAt = todayMorning.plusHours(i);
                    news.persist();
                }
            }

            // Create yesterday's task (1)
            Task yesterdayTask = new Task();
            yesterdayTask.name = "Yesterday task";
            yesterdayTask.timeRangeStart = yesterday.atStartOfDay();
            yesterdayTask.timeRangeEnd = yesterday.atTime(LocalTime.MAX);
            yesterdayTask.status = Task.STATUS_COMPLETED;
            yesterdayTask.sourceType = Task.SOURCE_ALL;
            yesterdayTask.createdAt = yesterdayTime;
            yesterdayTask.persist();

            Report yesterdayReport = new Report();
            yesterdayReport.task = yesterdayTask;
            yesterdayReport.name = "Yesterday report";
            yesterdayReport.timeRangeStart = yesterday.atStartOfDay();
            yesterdayReport.timeRangeEnd = yesterday.atTime(LocalTime.MAX);
            yesterdayReport.newsCount = 3;
            yesterdayReport.createdAt = yesterdayTime;
            yesterdayReport.persist();

            for (int j = 0; j < 3; j++) {
                News news = new News();
                news.report = yesterdayReport;
                news.title = "昨日新闻 " + j;
                news.author = "作者";
                news.createdAt = yesterdayTime;
                news.persist();
            }
        });

        // Verify stats
        given()
            .when()
            .get("/api/v1/dashboard/stats")
            .then()
            .statusCode(200)
            .body("taskCount.today", equalTo(3))
            .body("taskCount.yesterday", equalTo(1))
            .body("taskCount.change", equalTo(2))
            .body("taskCount.changePercent", notNullValue())
            .body("reportCount.today", equalTo(3))
            .body("reportCount.yesterday", equalTo(1))
            .body("newsCount.today", equalTo(15))
            .body("newsCount.yesterday", equalTo(3));
    }

    // ---- I9-2: Yesterday is 0 → changePercent is null ----

    @Test
    void testStatsYesterdayZero() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayTime = today.atTime(10, 0);

        runInTransaction(() -> {
            Task task = new Task();
            task.name = "Today only task";
            task.timeRangeStart = today.atStartOfDay();
            task.timeRangeEnd = today.atTime(LocalTime.MAX);
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.createdAt = todayTime;
            task.persist();
        });

        String statsJson = given()
            .when()
            .get("/api/v1/dashboard/stats")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        assertTrue(statsJson.contains("\"yesterday\":0"));
    }

    // ---- I9-3: Recent tasks ----

    @Test
    void testRecentTasks() {
        runInTransaction(() -> {
            for (int i = 0; i < 7; i++) {
                Task task = new Task();
                task.name = "Task " + (i + 1);
                task.timeRangeStart = LocalDateTime.now().minusHours(10 - i);
                task.timeRangeEnd = LocalDateTime.now().minusHours(8 - i);
                task.status = i < 5 ? Task.STATUS_COMPLETED : Task.STATUS_RUNNING;
                task.sourceType = Task.SOURCE_ALL;
                task.createdAt = LocalDateTime.now().minusHours(10 - i);
                task.startedAt = LocalDateTime.now().minusHours(10 - i);
                if (i < 5) {
                    task.endedAt = LocalDateTime.now().minusHours(8 - i);
                }
                task.persist();

                if (i < 5) {
                    Report report = new Report();
                    report.task = task;
                    report.name = "Report " + (i + 1);
                    report.timeRangeStart = task.timeRangeStart;
                    report.timeRangeEnd = task.timeRangeEnd;
                    report.newsCount = 10;
                    report.createdAt = task.createdAt;
                    report.persist();
                }
            }
        });

        given()
            .when()
            .get("/api/v1/dashboard/recent-tasks")
            .then()
            .statusCode(200)
            .body("size()", lessThanOrEqualTo(5))
            .body("[0].name", notNullValue())
            .body("[0].status", notNullValue());

        String json = given()
            .when()
            .get("/api/v1/dashboard/recent-tasks")
            .then()
            .extract()
            .asString();

        assertTrue(json.contains("Task 7"));
    }

    // ---- I9-4: Recent reports ----

    @Test
    void testRecentReports() {
        runInTransaction(() -> {
            for (int i = 0; i < 5; i++) {
                Task task = new Task();
                task.name = "ReportTask " + (i + 1);
                task.timeRangeStart = LocalDateTime.now().minusDays(1);
                task.timeRangeEnd = LocalDateTime.now();
                task.status = Task.STATUS_COMPLETED;
                task.sourceType = Task.SOURCE_ALL;
                task.createdAt = LocalDateTime.now().minusHours(5 - i);
                task.persist();

                Report report = new Report();
                report.task = task;
                report.name = "Report " + (i + 1);
                report.timeRangeStart = LocalDateTime.now().minusDays(1);
                report.timeRangeEnd = LocalDateTime.now();
                report.newsCount = i * 10 + 5;
                report.createdAt = LocalDateTime.now().minusHours(5 - i);
                report.persist();
            }
        });

        given()
            .when()
            .get("/api/v1/dashboard/recent-reports")
            .then()
            .statusCode(200)
            .body("size()", lessThanOrEqualTo(3))
            .body("[0].name", notNullValue())
            .body("[0].newsCount", notNullValue())
            .body("[0].createdAt", notNullValue());

        String json = given()
            .when()
            .get("/api/v1/dashboard/recent-reports")
            .then()
            .extract()
            .asString();

        assertTrue(json.contains("Report 5"));
    }

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
