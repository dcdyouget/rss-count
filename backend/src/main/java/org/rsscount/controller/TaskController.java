package org.rsscount.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.entity.Report;
import org.rsscount.entity.Task;
import org.rsscount.service.TaskExecutor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskController {

    @Inject
    TaskExecutor taskExecutor;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── DTOs ───────────────────────────────────────────────

    public record TaskListItem(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long reportId,
        String errorMessage
    ) {}

    public record TaskDetailItem(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        String status,
        String sourceType,
        String sourceConfig,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long reportId,
        String errorMessage,
        List<SourceInfo> sources,
        List<NewsBrief> news
    ) {}

    public record SourceInfo(Long id, String name, int fetchedCount) {}

    public record NewsBrief(Long id, String title, String sourceRssName) {}

    public record PagedResponse<T>(long total, int page, int size, List<T> items) {}

    public record CreateTaskRequest(
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        String sourceType,
        Map<String, Object> sourceConfig
    ) {}

    public record CreateTaskResponse(Long taskId, Long reportId) {}

    public record LastEndTimeResponse(LocalDateTime endedAt) {}

    // ── 5. GET /tasks — List with pagination and filters ───

    @GET
    public PagedResponse<TaskListItem> list(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("20") int size,
        @QueryParam("status") String status,
        @QueryParam("createdAfter") String createdAfter,
        @QueryParam("createdBefore") String createdBefore
    ) {
        StringBuilder query = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();

        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", status);
        }

        if (createdAfter != null && !createdAfter.isBlank()) {
            query.append(" and createdAt >= :createdAfter");
            params.put("createdAfter", LocalDateTime.parse(createdAfter));
        }

        if (createdBefore != null && !createdBefore.isBlank()) {
            query.append(" and createdAt <= :createdBefore");
            params.put("createdBefore", LocalDateTime.parse(createdBefore));
        }

        var panacheQuery = Task.find(query.toString(), Sort.by("createdAt").descending(), params);
        long total = panacheQuery.count();
        List<Task> tasks = panacheQuery.page(Page.of(page - 1, size)).list();

        List<TaskListItem> items = tasks.stream()
            .map(this::toListItem)
            .collect(Collectors.toList());

        return new PagedResponse<>(total, page, size, items);
    }

    // ── 6. GET /tasks/{id} — Task detail ───────────────────

    @GET
    @Path("/{id}")
    public TaskDetailItem getDetail(
        @PathParam("id") Long id,
        @QueryParam("newsPage") @DefaultValue("1") int newsPage,
        @QueryParam("newsSize") @DefaultValue("20") int newsSize
    ) {
        Task task = Task.findById(id);
        if (task == null) {
            throw new NotFoundException("任务不存在");
        }

        // Find associated report
        Report report = Report.find("task.id", task.id).firstResult();

        // Build source list from task details if available
        List<SourceInfo> sources = new ArrayList<>();

        // Build news list if COMPLETED and report exists
        List<NewsBrief> news = new ArrayList<>();
        Long reportId = null;
        if (report != null) {
            reportId = report.id;
            if (Task.STATUS_COMPLETED.equals(task.status)) {
                var newsQuery = org.rsscount.entity.News.find(
                    "report.id", Sort.by("createdAt").descending(), report.id);
                List<org.rsscount.entity.News> newsEntities = newsQuery
                    .page(Page.of(newsPage - 1, newsSize)).list();
                news = newsEntities.stream()
                    .map(n -> new NewsBrief(n.id, n.title, n.sourceRssName))
                    .collect(Collectors.toList());
            }
        }

        return new TaskDetailItem(
            task.id, task.name, task.timeRangeStart, task.timeRangeEnd,
            task.status, task.sourceType, task.sourceConfig,
            task.startedAt, task.endedAt, reportId,
            task.errorMessage, sources, news
        );
    }

    // ── 4. POST /tasks — Create and execute ────────────────

    @POST
    @Transactional
    public Response create(CreateTaskRequest request) {
        // Validate
        if (request.name == null || request.name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "任务名称不能为空")).build();
        }
        if (request.timeRangeStart != null && request.timeRangeEnd != null
            && !request.timeRangeStart.isBefore(request.timeRangeEnd)) {
            return Response.status(422)
                .entity(Map.of("error", "时间范围无效: timeRangeStart 必须早于 timeRangeEnd"))
                .build();
        }

        // Determine sourceType and serialize sourceConfig
        String sourceType = request.sourceType != null ? request.sourceType : Task.SOURCE_ALL;
        String sourceConfigJson = null;
        if (request.sourceConfig != null && !request.sourceConfig.isEmpty()) {
            try {
                sourceConfigJson = MAPPER.writeValueAsString(request.sourceConfig);
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "sourceConfig格式错误")).build();
            }
        }

        // Create Task
        Task task = new Task();
        task.name = request.name;
        task.timeRangeStart = request.timeRangeStart != null
            ? request.timeRangeStart : LocalDateTime.now().minusHours(24);
        task.timeRangeEnd = request.timeRangeEnd != null
            ? request.timeRangeEnd : LocalDateTime.now();
        task.status = Task.STATUS_RUNNING;
        task.sourceType = sourceType;
        task.sourceConfig = sourceConfigJson;
        task.startedAt = LocalDateTime.now();
        task.persist();

        // Create Report
        Report report = new Report();
        report.task = task;
        report.name = request.name.replace("任务", "报告");
        report.timeRangeStart = task.timeRangeStart;
        report.timeRangeEnd = task.timeRangeEnd;
        report.persist();

        // Async execution
        taskExecutor.execute(task);

        Log.infof("Task %d created and started: %s", task.id, task.name);

        return Response.status(Response.Status.CREATED)
            .entity(new CreateTaskResponse(task.id, report.id))
            .build();
    }

    // ── 8. GET /tasks/suggest-name ─────────────────────────

    @GET
    @Path("/suggest-name")
    @Produces(MediaType.TEXT_PLAIN)
    public String suggestName() {
        String today = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

        // Find the latest completed task today to determine the sequence number
        LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long countToday = Task.count("createdAt >= ?1", dayStart);

        return today + "-第" + (countToday + 1) + "次任务";
    }

    // ── 9. GET /tasks/last-end-time ────────────────────────

    @GET
    @Path("/last-end-time")
    public Response lastEndTime() {
        Task lastCompleted = Task.find("status = ?1 order by endedAt desc", Task.STATUS_COMPLETED)
            .firstResult();

        if (lastCompleted == null || lastCompleted.endedAt == null) {
            return Response.ok(new LastEndTimeResponse(null)).build();
        }

        return Response.ok(new LastEndTimeResponse(lastCompleted.endedAt)).build();
    }

    // ── 7. GET /tasks/{id}/stream — SSE ────────────────────

    @GET
    @Path("/{id}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@PathParam("id") Long id,
                       @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.SseEventSink eventSink,
                       @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.Sse sse) {
        Task task = Task.findById(id);
        if (task == null) {
            try (jakarta.ws.rs.sse.SseEventSink s = eventSink) {
                s.send(sse.newEvent("error", "{\"message\":\"任务不存在\"}"));
            } catch (Exception ignored) {}
            return;
        }

        // If task is already completed or failed, send the terminal event directly
        if (Task.STATUS_COMPLETED.equals(task.status)) {
            Report report = Report.find("task.id", task.id).firstResult();
            try (jakarta.ws.rs.sse.SseEventSink s = eventSink) {
                s.send(sse.newEvent("complete",
                    "{\"reportId\":" + (report != null ? report.id : null) + "}"));
            } catch (Exception ignored) {}
            return;
        }
        if (Task.STATUS_FAILED.equals(task.status)) {
            try (jakarta.ws.rs.sse.SseEventSink s = eventSink) {
                s.send(sse.newEvent("error",
                    "{\"message\":\"" + (task.errorMessage != null ? task.errorMessage : "") + "\"}"));
            } catch (Exception ignored) {}
            return;
        }

        // Register progress callback
        java.util.function.Consumer<TaskExecutor.SseProgressEvent> callback = event -> {
            try {
                String jsonData;
                if (event.data() instanceof String) {
                    jsonData = (String) event.data();
                } else {
                    jsonData = taskExecutor.toJsonSafely(event.data());
                }
                eventSink.send(sse.newEvent(event.event(), jsonData));
            } catch (Exception e) {
                // Client disconnected
                Log.debugf("SSE client disconnected from task %d", id);
            }
        };

        taskExecutor.subscribe(id, callback);

        // Keep connection alive until client disconnects
        while (!eventSink.isClosed()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        taskExecutor.unsubscribe(id, callback);
    }

    // ── Private helpers ────────────────────────────────────

    private TaskListItem toListItem(Task task) {
        Long reportId = null;
        if (Task.STATUS_COMPLETED.equals(task.status)) {
            Report report = Report.find("task.id", task.id).firstResult();
            if (report != null) {
                reportId = report.id;
            }
        }

        return new TaskListItem(
            task.id, task.name, task.timeRangeStart, task.timeRangeEnd,
            task.status, task.startedAt, task.endedAt, reportId, task.errorMessage
        );
    }
}
