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
import org.rsscount.service.TaskExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * I10: TaskController integration tests.
 *
 * I10-1: POST /tasks creates task with 201 response.
 * I10-2: GET /tasks returns paginated list.
 * I10-3: GET /tasks/{id} returns detail.
 * I10-4: GET /tasks/suggest-name returns name string.
 * I10-5: GET /tasks/last-end-time returns time or null.
 * I10-6: GET /tasks/{id}/stream returns SSE events.
 */
@QuarkusTest
class TaskControllerTest {

    @Inject
    UserTransaction userTransaction;

    @InjectMock
    TaskExecutor mockTaskExecutor;

    @BeforeEach
    @Transactional
    void cleanup() {
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    // ── I10-1: POST /tasks ─────────────────────────────────

    @Test
    void testCreateTask() {
        Mockito.doNothing().when(mockTaskExecutor).execute(Mockito.any());

        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "测试创建任务",
                    "timeRangeStart": "2026-05-20T08:00:00",
                    "timeRangeEnd": "2026-05-20T14:00:00",
                    "sourceType": "ALL"
                }
                """)
            .when()
            .post("/api/v1/tasks")
            .then()
            .statusCode(201)
            .body("taskId", notNullValue())
            .body("reportId", notNullValue());
    }

    @Test
    void testCreateTaskWithEmptyNameFails() {
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "",
                    "sourceType": "ALL"
                }
                """)
            .when()
            .post("/api/v1/tasks")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateTaskWithInvalidTimeRange() {
        given()
            .contentType("application/json")
            .body("""
                {
                    "name": "时间错误任务",
                    "timeRangeStart": "2026-05-20T14:00:00",
                    "timeRangeEnd": "2026-05-20T08:00:00",
                    "sourceType": "ALL"
                }
                """)
            .when()
            .post("/api/v1/tasks")
            .then()
            .statusCode(422);
    }

    // ── I10-2: GET /tasks ──────────────────────────────────

    @Test
    void testListTasks() throws Exception {
        runInTransaction(() -> {
            for (int i = 0; i < 3; i++) {
                Task task = new Task();
                task.name = "列表任务" + (i + 1);
                task.timeRangeStart = LocalDateTime.now().minusDays(1);
                task.timeRangeEnd = LocalDateTime.now();
                task.status = i == 0 ? Task.STATUS_COMPLETED : Task.STATUS_RUNNING;
                task.sourceType = Task.SOURCE_ALL;
                task.createdAt = LocalDateTime.now().minusHours(3 - i);
                task.startedAt = LocalDateTime.now().minusHours(3 - i);
                if (i == 0) {
                    task.endedAt = LocalDateTime.now().minusHours(2);
                }
                task.persist();

                if (i == 0) {
                    Report report = new Report();
                    report.task = task;
                    report.name = "列表报告1";
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
            .queryParam("page", 1)
            .queryParam("size", 20)
            .get("/api/v1/tasks")
            .then()
            .statusCode(200)
            .body("total", equalTo(3))
            .body("items.size()", equalTo(3))
            .body("items[0].name", notNullValue())
            .body("items[0].status", notNullValue());
    }

    @Test
    void testListTasksWithStatusFilter() throws Exception {
        runInTransaction(() -> {
            Task completed = new Task();
            completed.name = "已完成";
            completed.timeRangeStart = LocalDateTime.now().minusDays(1);
            completed.timeRangeEnd = LocalDateTime.now();
            completed.status = Task.STATUS_COMPLETED;
            completed.sourceType = Task.SOURCE_ALL;
            completed.createdAt = LocalDateTime.now();
            completed.startedAt = LocalDateTime.now().minusHours(1);
            completed.endedAt = LocalDateTime.now();
            completed.persist();

            Task running = new Task();
            running.name = "运行中";
            running.timeRangeStart = LocalDateTime.now().minusDays(1);
            running.timeRangeEnd = LocalDateTime.now();
            running.status = Task.STATUS_RUNNING;
            running.sourceType = Task.SOURCE_ALL;
            running.createdAt = LocalDateTime.now();
            running.startedAt = LocalDateTime.now();
            running.persist();
        });

        given()
            .when()
            .queryParam("status", "COMPLETED")
            .get("/api/v1/tasks")
            .then()
            .statusCode(200)
            .body("total", equalTo(1))
            .body("items[0].status", equalTo("COMPLETED"));
    }

    // ── I10-3: GET /tasks/{id} ─────────────────────────────

    @Test
    void testGetTaskDetail() throws Exception {
        runInTransaction(() -> {
            Task task = new Task();
            task.name = "详情任务";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_RUNNING;
            task.sourceType = Task.SOURCE_ALL;
            task.startedAt = LocalDateTime.now();
            task.persist();
        });

        given()
            .when()
            .get("/api/v1/tasks/1")
            .then()
            .statusCode(200)
            .body("name", notNullValue());
    }

    @Test
    void testGetTaskDetailNotFound() {
        given()
            .when()
            .get("/api/v1/tasks/99999")
            .then()
            .statusCode(404);
    }

    // ── I10-4: GET /tasks/suggest-name ─────────────────────

    @Test
    void testSuggestName() {
        String name = given()
            .when()
            .get("/api/v1/tasks/suggest-name")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        assertNotNull(name);
        assertTrue(name.contains("任务"));
    }

    // ── I10-5: GET /tasks/last-end-time ────────────────────

    @Test
    void testLastEndTimeWhenNoHistory() {
        given()
            .when()
            .get("/api/v1/tasks/last-end-time")
            .then()
            .statusCode(200)
            .body("endedAt", nullValue());
    }

    @Test
    void testLastEndTimeWithHistory() throws Exception {
        runInTransaction(() -> {
            Task task = new Task();
            task.name = "历史任务";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.startedAt = LocalDateTime.now().minusHours(2);
            task.endedAt = LocalDateTime.now().minusHours(1);
            task.persist();
        });

        given()
            .when()
            .get("/api/v1/tasks/last-end-time")
            .then()
            .statusCode(200)
            .body("endedAt", notNullValue());
    }

    // ── I10-6: GET /tasks/{id}/stream ──────────────────────

    @Test
    void testStreamForCompletedTask() throws Exception {
        runInTransaction(() -> {
            Task task = new Task();
            task.name = "已完成SSE任务";
            task.timeRangeStart = LocalDateTime.now().minusDays(1);
            task.timeRangeEnd = LocalDateTime.now();
            task.status = Task.STATUS_COMPLETED;
            task.sourceType = Task.SOURCE_ALL;
            task.startedAt = LocalDateTime.now().minusHours(1);
            task.endedAt = LocalDateTime.now();
            task.persist();

            Report report = new Report();
            report.task = task;
            report.name = "SSE报告";
            report.timeRangeStart = task.timeRangeStart;
            report.timeRangeEnd = task.timeRangeEnd;
            report.persist();
        });

        // Just test that it returns 200 with SSE content type
        given()
            .when()
            .get("/api/v1/tasks/1/stream")
            .then()
            .statusCode(200)
            .contentType(containsString("text/event-stream"));
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
