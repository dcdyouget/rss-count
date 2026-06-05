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

/**
 * 任务管理 REST 接口。
 * 路由前缀: /api/v1/tasks
 * 负责任务的创建、查询、详情查看以及 SSE 进度推送。
 */
@Path("/api/v1/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskController {

    @Inject
    TaskExecutor taskExecutor;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── DTOs ───────────────────────────────────────────────

    /**
     * 任务列表项 DTO，用于任务分页列表展示。
     * @param id 任务ID
     * @param name 任务名称
     * @param timeRangeStart 任务查询时间范围（开始）
     * @param timeRangeEnd 任务查询时间范围（结束）
     * @param status 任务状态（RUNNING / COMPLETED / FAILED）
     * @param startedAt 任务开始执行时间
     * @param endedAt 任务结束时间
     * @param reportId 关联的报告ID（任务完成后生成）
     * @param errorMessage 任务失败时的错误信息
     */
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

    /**
     * 任务详情 DTO，包含任务基本信息、关联源列表和新闻列表。
     * @param id 任务ID
     * @param name 任务名称
     * @param timeRangeStart 任务查询时间范围（开始）
     * @param timeRangeEnd 任务查询时间范围（结束）
     * @param status 任务状态
     * @param sourceType 源类型（ALL / SPECIFIC）
     * @param sourceConfig 源配置 JSON
     * @param startedAt 任务开始时间
     * @param endedAt 任务结束时间
     * @param reportId 关联报告ID
     * @param errorMessage 错误信息
     * @param sources 关联的 RSS 源列表
     * @param news 关联的新闻列表（仅已完成任务）
     */
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

    /**
     * RSS 源概要信息。
     * @param id 源ID
     * @param name 源名称
     * @param fetchedCount 抓取数量
     */
    public record SourceInfo(Long id, String name, int fetchedCount) {}

    /**
     * 新闻概要信息。
     * @param id 新闻ID
     * @param title 新闻标题
     * @param sourceRssName 来源 RSS 名称
     */
    public record NewsBrief(Long id, String title, String sourceRssName) {}

    /**
     * 通用分页响应包装。
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页条数
     * @param items 当前页数据列表
     */
    public record PagedResponse<T>(long total, int page, int size, List<T> items) {}

    /**
     * 创建任务的请求体。
     * @param name 任务名称
     * @param timeRangeStart 抓取时间范围（开始），默认24小时前
     * @param timeRangeEnd 抓取时间范围（结束），默认为当前时间
     * @param sourceType 源类型，ALL 表示所有源，SPECIFIC 表示指定源
     * @param sourceConfig 源配置，当 sourceType=SPECIFIC 时传递具体源ID等信息
     */
    public record CreateTaskRequest(
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        String sourceType,
        Map<String, Object> sourceConfig
    ) {}

    /**
     * 创建任务的响应体。
     * @param taskId 创建的任务ID
     * @param reportId 关联的报告ID
     */
    public record CreateTaskResponse(Long taskId, Long reportId) {}

    /**
     * 最后一次任务结束时间响应。
     * @param endedAt 最后一次已完成任务的结束时间
     */
    public record LastEndTimeResponse(LocalDateTime endedAt) {}

    // ── 5. GET /tasks — List with pagination and filters ───

    /**
     * 分页查询任务列表，支持按状态和时间筛选。
     * @param page 页码（1-based）
     * @param size 每页条数，默认20
     * @param status 任务状态筛选（可选）
     * @param createdAfter 创建时间下限（可选，ISO 格式）
     * @param createdBefore 创建时间上限（可选，ISO 格式）
     * @return 分页响应，按创建时间倒序排列
     */
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

    /**
     * 获取指定任务的详细信息，包括关联的源列表和新闻列表（已完成任务）。
     * @param id 任务ID
     * @param newsPage 新闻分页页码（1-based）
     * @param newsSize 新闻每页条数
     * @return 任务详情
     * @throws NotFoundException 任务不存在时抛出
     */
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

    /**
     * 创建新任务并异步执行。自动生成对应的报告记录。
     * @param request 创建任务的请求体
     * @return 201 Created，包含 taskId 和 reportId
     */
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

        // Async execution (creates Report internally via findOrCreateReport)
        taskExecutor.execute(task);

        Log.infof("Task %d created and started: %s", task.id, task.name);

        return Response.status(Response.Status.CREATED)
            .entity(new CreateTaskResponse(task.id, null))
            .build();
    }

    // ── 8. GET /tasks/suggest-name ─────────────────────────

    /**
     * 自动建议下一个任务名称，格式为 "yyyy年M月d日-第N次任务"。
     * @return 建议的任务名称（纯文本）
     */
    @GET
    @Path("/suggest-name")
    @Produces(MediaType.TEXT_PLAIN)
    public String suggestName() {
        String today = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

        // Find the latest completed task today to determine the sequence number
        // Find the max sequence number for today's tasks to avoid duplicates
        String todayPrefix = today + "-第";
        Task latestToday = Task.find("name like ?1 order by createdAt desc", todayPrefix + "%").firstResult();
        int nextSeq = 1;
        if (latestToday != null) {
            String n = latestToday.name;
            int startIdx = n.indexOf(todayPrefix) + todayPrefix.length();
            int endIdx = n.indexOf("次", startIdx);
            if (endIdx > startIdx) {
                try {
                    nextSeq = Integer.parseInt(n.substring(startIdx, endIdx)) + 1;
                } catch (NumberFormatException e) {
                    nextSeq = 2;
                }
            }
        }

        return today + "-第" + nextSeq + "次任务";
    }

    // ── 9. GET /tasks/last-end-time ────────────────────────

    /**
     * 查询最后一次已完成任务的结束时间。
     * @return 最后一次结束时间，没有已完成任务时返回 null
     */
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

    /**
     * SSE (Server-Sent Events) 端点，实时推送任务执行进度。
     * 客户端可通过此端点接收任务运行过程中的状态更新。
     * @param id 任务ID
     * @param eventSink SSE 事件通道
     * @param sse SSE 工厂对象
     */
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

        // Register progress callback FIRST, before checking status, to avoid
        // a TOCTOU race where the task completes between the status check and
        // the subscribe call.
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

        try {
            // Re-check task status from DB (after subscribing) to avoid TOCTOU race
            Task freshTask = Task.findById(id);
            if (Task.STATUS_COMPLETED.equals(freshTask.status)) {
                Report report = Report.find("task.id", freshTask.id).firstResult();
                callback.accept(new TaskExecutor.SseProgressEvent("complete",
                    "{\"reportId\":" + (report != null ? report.id : null) + "}"));
                return;
            }
            if (Task.STATUS_FAILED.equals(freshTask.status)) {
                callback.accept(new TaskExecutor.SseProgressEvent("error",
                    "{\"message\":\"" + (freshTask.errorMessage != null ? freshTask.errorMessage : "") + "\"}"));
                return;
            }

            // Keep connection alive until client disconnects
            while (!eventSink.isClosed()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } finally {
            taskExecutor.unsubscribe(id, callback);
        }
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
