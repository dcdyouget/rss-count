package org.rsscount.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.rsscount.entity.*;
import org.rsscount.util.TimeNormalizer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Task executor — orchestrates the full task pipeline (fetch → format → complete/fail)
 * with Virtual Threads and SSE progress push.
 */
@ApplicationScoped
public class TaskExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    RssFetchService rssFetchService;

    @Inject
    NewsFormatService newsFormatService;

    /** Format concurrency: fixed pool of 5 threads for AI-intensive formatting */
    private static final int FORMAT_THREADS = 5;

    /** Progress emitters per task (supports multiple clients per task) */
    private final ConcurrentMap<Long, List<Consumer<SseProgressEvent>>> emitters = new ConcurrentHashMap<>();

    /** Running task references, used for cancellation checks */
    private final ConcurrentMap<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    // ── SSE event types ────────────────────────────────────

    /** Progress event data sent to SSE subscribers */
    public record SseProgressEvent(
        String event,       // "progress", "complete", "error"
        Object data
    ) {}

    public record PullingProgress(
        boolean done,
        String currentSource,
        String sourceProgress,
        int totalFetched
    ) {}

    public record FormattingProgress(
        boolean done,
        int formatted,
        int total,
        String currentAction
    ) {}

    public record CombinedProgress(
        PullingProgress pulling,
        FormattingProgress formatting
    ) {}

    public record CompleteData(Long reportId) {}

    public record ErrorData(String message) {}

    // ── Lifecycle ──────────────────────────────────────────

    @PostConstruct
    void resumeRunningTasks() {
        Log.info("TaskExecutor: Checking for incomplete tasks...");
        try {
            List<Task> runningTasksList = Task.find("status", Task.STATUS_RUNNING).list();
            if (runningTasksList.isEmpty()) {
                Log.info("TaskExecutor: No incomplete tasks found.");
                return;
            }

            Log.infof("TaskExecutor: Resuming %d running task(s).", runningTasksList.size());
            for (Task task : runningTasksList) {
                Log.infof("TaskExecutor: Resuming task %d (%s)", task.id, task.name);
                execute(task);
            }
        } catch (Exception e) {
            Log.errorf("TaskExecutor: Failed to resume running tasks: %s", e.getMessage());
        }
    }

    // ── Public API ─────────────────────────────────────────

    /**
     * Execute a task: fetch → format → complete/fail.
     * Runs asynchronously on Virtual Threads.
     */
    public void execute(Task task) {
        Long taskId = task.id;
        runningTasks.put(taskId, true);

        Thread.startVirtualThread(() -> {
            try {
                doExecute(task);
            } catch (Exception e) {
                Log.errorf(e, "Task %d execution failed unexpectedly", taskId);
                markTaskFailed(task, "系统异常: " + e.getMessage());
            } finally {
                runningTasks.remove(taskId);
            }
        });
    }

    /**
     * Subscribe to SSE events for a task.
     * Returns a Consumer that can be used to unsubscribe.
     */
    public Consumer<SseProgressEvent> subscribe(Long taskId, Consumer<SseProgressEvent> callback) {
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(callback);
        return callback;
    }

    /**
     * Unsubscribe a callback from task SSE events.
     */
    public void unsubscribe(Long taskId, Consumer<SseProgressEvent> callback) {
        List<Consumer<SseProgressEvent>> list = emitters.get(taskId);
        if (list != null) {
            list.remove(callback);
            if (list.isEmpty()) {
                emitters.remove(taskId);
            }
        }
    }

    /**
     * Check if a task is currently running.
     */
    public boolean isRunning(Long taskId) {
        return runningTasks.containsKey(taskId);
    }

    // ── Internal execution ─────────────────────────────────

    @Transactional
    protected void doExecute(Task task) {
        Long taskId = task.id;
        Log.infof("Task %d: Starting execution", taskId);

        try {
            // 1. Resolve sources
            List<RssSource> sources = resolveSources(task);

            if (sources.isEmpty()) {
                markTaskFailed(task, "未找到有效的RSS源");
                return;
            }

            // 2. Create or get report
            Report report = findOrCreateReport(task);

            // 3. Fetch phase: concurrent pull from all RSS sources
            AtomicInteger totalFetched = new AtomicInteger(0);
            AtomicInteger completedSources = new AtomicInteger(0);
            int totalSources = sources.size();
            List<News> allNews = Collections.synchronizedList(new ArrayList<>());
            List<String> fetchErrors = Collections.synchronizedList(new ArrayList<>());

            // 3a. Load existing news for dedup
            List<News> existingNews = loadExistingNews(report);

            try (ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<List<News>>> futures = new ArrayList<>();

                for (RssSource source : sources) {
                    futures.add(fetchExecutor.submit(() -> {
                        try {
                            emitProgress(taskId, "progress", new CombinedProgress(
                                new PullingProgress(false, source.name,
                                    (completedSources.get() + 1) + "/" + totalSources,
                                    totalFetched.get()),
                                new FormattingProgress(false, 0, 0, "正在拉取")
                            ));

                            int count = rssFetchService.fetchFromSource(source, task, report);
                            totalFetched.addAndGet(count);
                            completedSources.incrementAndGet();
                            return Collections.<News>emptyList();
                        } catch (Exception e) {
                            String msg = source.name + ": " + e.getMessage();
                            fetchErrors.add(msg);
                            completedSources.incrementAndGet();
                            Log.errorf("Task %d: Source fetch failed: %s", taskId, msg);
                            return Collections.<News>emptyList();
                        }
                    }));
                }

                for (Future<List<News>> future : futures) {
                    try {
                        List<News> news = future.get(60, TimeUnit.SECONDS);
                        allNews.addAll(news);
                    } catch (Exception e) {
                        fetchErrors.add("拉取超时或失败: " + e.getMessage());
                    }
                }
            }

            emitProgress(taskId, "progress", new CombinedProgress(
                new PullingProgress(true, "", totalSources + "/" + totalSources, totalFetched.get()),
                new FormattingProgress(false, 0, allNews.size(), "正在格式化")
            ));

            // 4. Format phase: process news through formatting pipeline
            List<News> freshNews = new ArrayList<>(allNews);
            AtomicInteger formattedCount = new AtomicInteger(0);
            int totalToFormat = freshNews.size();

            if (totalToFormat == 0) {
                // Check if all sources failed
                if (fetchErrors.size() >= totalSources) {
                    markTaskFailed(task, "全部RSS源拉取失败：" + String.join("; ", fetchErrors));
                    return;
                }
            }

            try (ExecutorService formatExecutor = Executors.newFixedThreadPool(FORMAT_THREADS)) {
                List<CompletableFuture<Void>> formatFutures = new ArrayList<>();

                for (News news : freshNews) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            // Re-fetch managed entity for this thread's context
                            News managedNews = News.findById(news.id);
                            if (managedNews != null) {
                                Report managedReport = Report.findById(report.id);
                                newsFormatService.formatOneNews(managedNews, managedReport);
                                int done = formattedCount.incrementAndGet();
                                emitProgress(taskId, "progress", new CombinedProgress(
                                    new PullingProgress(true, "", totalSources + "/" + totalSources, totalFetched.get()),
                                    new FormattingProgress(false, done, totalToFormat, "正在生成概览")
                                ));
                            }
                        } catch (Exception e) {
                            Log.debugf("Task %d: Format skip for news: %s", taskId, e.getMessage());
                            formattedCount.incrementAndGet();
                        }
                    }, formatExecutor);
                    formatFutures.add(future);
                }

                // Wait for all formatting to complete
                CompletableFuture.allOf(formatFutures.toArray(new CompletableFuture[0]))
                    .get(300, TimeUnit.SECONDS);
            }

            // 5. Mark task as completed
            emitProgress(taskId, "progress", new CombinedProgress(
                new PullingProgress(true, "", totalSources + "/" + totalSources, totalFetched.get()),
                new FormattingProgress(true, formattedCount.get(), totalToFormat, "格式化完成")
            ));

            markTaskCompleted(task, report);

        } catch (Exception e) {
            Log.errorf(e, "Task %d execution error", taskId);
            markTaskFailed(task, "执行异常: " + e.getMessage());
        }
    }

    // ── Source resolution ──────────────────────────────────

    private List<RssSource> resolveSources(Task task) {
        String sourceType = task.sourceType;
        String sourceConfig = task.sourceConfig;

        // Default: ALL
        if (sourceType == null || Task.SOURCE_ALL.equals(sourceType)) {
            return RssSource.list("isActive", true);
        }

        if (sourceConfig == null || sourceConfig.isBlank()) {
            return List.of();
        }

        try {
            com.fasterxml.jackson.databind.JsonNode config = MAPPER.readTree(sourceConfig);
            Set<Long> sourceIds = new HashSet<>();

            if (Task.SOURCE_GROUP.equals(sourceType) || Task.SOURCE_MIXED.equals(sourceType)) {
                // Expand groups
                if (config.has("groupIds") && config.get("groupIds").isArray()) {
                    for (var node : config.get("groupIds")) {
                        Long groupId = node.asLong();
                        List<RssSourceGroup> mappings = RssSourceGroup.find("groupId", groupId).list();
                        for (RssSourceGroup mapping : mappings) {
                            sourceIds.add(mapping.rssSourceId);
                        }
                    }
                }
            }

            if (Task.SOURCE_SOURCE.equals(sourceType) || Task.SOURCE_MIXED.equals(sourceType)) {
                if (config.has("sourceIds") && config.get("sourceIds").isArray()) {
                    for (var node : config.get("sourceIds")) {
                        sourceIds.add(node.asLong());
                    }
                }
            }

            if (sourceIds.isEmpty()) {
                return List.of();
            }

            return RssSource.list("id in ?1 and isActive = true", sourceIds);
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to parse sourceConfig for task %d: %s", task.id, e.getMessage());
            return List.of();
        }
    }

    // ── Report management ──────────────────────────────────

    @Transactional
    Report findOrCreateReport(Task task) {
        Report report = Report.find("task.id", task.id).firstResult();
        if (report == null) {
            report = new Report();
            report.task = task;
            report.name = task.name != null ? task.name.replace("任务", "报告") : ("报告#" + task.id);
            report.timeRangeStart = task.timeRangeStart;
            report.timeRangeEnd = task.timeRangeEnd;
            report.persist();
        }
        return report;
    }

    @Transactional
    void markTaskCompleted(Task task, Report report) {
        Task managed = Task.findById(task.id);
        if (managed == null) return;

        managed.status = Task.STATUS_COMPLETED;
        managed.endedAt = LocalDateTime.now();

        // Update report news count
        Report managedReport = Report.findById(report.id);
        if (managedReport != null) {
            long count = News.count("report.id", managedReport.id);
            managedReport.newsCount = (int) count;
        }

        // Clear emitter list
        emitters.remove(task.id);

        emitProgress(task.id, "complete", new CompleteData(report.id));
        Log.infof("Task %d: Completed with report %d", task.id, report.id);
    }

    @Transactional
    void markTaskFailed(Task task, String errorMessage) {
        Task managed = Task.findById(task.id);
        if (managed == null) return;

        managed.status = Task.STATUS_FAILED;
        managed.endedAt = LocalDateTime.now();
        managed.errorMessage = errorMessage;

        // Clear emitter list
        emitters.remove(task.id);

        emitProgress(task.id, "error", new ErrorData(errorMessage));
        Log.errorf("Task %d: Failed — %s", task.id, errorMessage);
    }

    // ── SSE emission ───────────────────────────────────────

    private void emitProgress(Long taskId, String event, Object data) {
        List<Consumer<SseProgressEvent>> list = emitters.get(taskId);
        if (list == null || list.isEmpty()) return;

        SseProgressEvent eventObj = new SseProgressEvent(event, data);
        String json = toJsonSafely(data);

        for (Consumer<SseProgressEvent> callback : list) {
            try {
                callback.accept(eventObj);
            } catch (Exception e) {
                // Client disconnected
                list.remove(callback);
            }
        }
    }

    public String toJsonSafely(Object data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // ── Existing news loader ───────────────────────────────

    List<News> loadExistingNews(Report report) {
        try {
            return News.find("report.id", report.id).list();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
