package org.rsscount.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
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

    /** Format concurrency: single-threaded — SQLite only supports 1 writer at a time,
     *  so parallel format threads would queue on the write lock anyway. */
    private static final int FORMAT_THREADS = 1;

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
            List<Task> runningTasksList = QuarkusTransaction.call(() ->
                Task.find("status", Task.STATUS_RUNNING).list());
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
                // Phase 1: Resolve sources in its own transaction.
                // Virtual threads have no CDI request context, so Panache queries
                // require an explicit transaction.
                List<RssSource> sources = QuarkusTransaction.call(() -> resolveSources(task));
                if (sources.isEmpty()) {
                    QuarkusTransaction.run(() -> markTaskFailed(task, "未找到有效的RSS源"));
                    return;
                }

                // Phase 2: Create/find report in its own transaction.
                // Must complete BEFORE format threads start writing so no outer
                // transaction holds a SQLite lock while format threads run.
                Report report = QuarkusTransaction.call(() -> findOrCreateReport(task));

                // Phase 3: Fetch + format (NO outer transaction — format threads
                // each use @Transactional(REQUIRES_NEW) to write independently)
                doExecute(task, report, sources);

                // Phase 4: Mark complete in its own transaction
                QuarkusTransaction.run(() -> markTaskCompleted(task, report));

            } catch (Exception e) {
                Log.errorf(e, "Task %d execution failed unexpectedly", taskId);
                try {
                    QuarkusTransaction.run(() ->
                        markTaskFailed(task, "系统异常: " + e.getMessage()));
                } catch (Exception innerErr) {
                    Log.errorf(innerErr, "Task %d markTaskFailed also failed", taskId);
                }
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

    protected void doExecute(Task task, Report report, List<RssSource> sources) throws Exception {
        Long taskId = task.id;
        Log.infof("Task %d: Starting fetch & format phase", taskId);

        // In-memory SimHash dedup map for current task
        //    Maps truncated hash → list of full hashes for hamming distance check
        ConcurrentHashMap<Long, CopyOnWriteArrayList<Long>> simHashSeen = new ConcurrentHashMap<>();
        int HASH_TRUNCATE_SHIFT = 16; // group by top 48 bits

        AtomicInteger totalFetched = new AtomicInteger(0);   // total entries fetched
        AtomicInteger totalNew = new AtomicInteger(0);       // non-duplicate (submitted to format)
        AtomicInteger sourceIndex = new AtomicInteger(0);
        AtomicInteger formattedCount = new AtomicInteger(0);
        int totalSources = sources.size();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // Format thread pool (persists after formatting)
        ExecutorService formatPool = Executors.newFixedThreadPool(FORMAT_THREADS);
        List<CompletableFuture<Void>> formatFutures = Collections.synchronizedList(new ArrayList<>());

        try {
            // Iterate sources sequentially, fetch entries, dedup in-memory, submit to format
            for (RssSource source : sources) {
                int idx = sourceIndex.incrementAndGet();
                Log.infof("Task %d: Fetching source [%d/%d] %s", taskId, idx, totalSources, source.name);

                emitProgress(taskId, "progress", new CombinedProgress(
                    new PullingProgress(false, source.name, idx + "/" + totalSources, totalNew.get()),
                    new FormattingProgress(false, formattedCount.get(), totalNew.get(), "正在拉取")
                ));

                try {
                    com.rometools.rome.feed.synd.SyndFeed feed = rssFetchService.parseFeed(source.url);
                    List<com.rometools.rome.feed.synd.SyndEntry> entries = feed.getEntries();

                    if (entries == null || entries.isEmpty()) {
                        Log.infof("Task %d: Source %s has no entries", taskId, source.name);
                        continue;
                    }

                    for (com.rometools.rome.feed.synd.SyndEntry entry : entries) {
                        totalFetched.incrementAndGet();

                        try {
                            // Convert entry to raw News (no DB access)
                            News news = rssFetchService.syndEntryToNews(entry, source, report);
                            if (news == null || news.title == null || news.title.isBlank()) {
                                continue;
                            }

                            // In-memory dedup: check SimHash against map
                            if (news.simHash != null && news.simHash != 0L) {
                                Long key = news.simHash >>> HASH_TRUNCATE_SHIFT;
                                CopyOnWriteArrayList<Long> bucket = simHashSeen.get(key);

                                if (bucket != null) {
                                    boolean isDup = false;
                                    for (Long existingHash : bucket) {
                                        if (Long.bitCount(news.simHash ^ existingHash) <= 3) {
                                            isDup = true;
                                            break;
                                        }
                                    }
                                    if (isDup) {
                                        Log.debugf("Task %d: SimHash dedup hit: %s", taskId, news.title);
                                        continue;
                                    }
                                    bucket.add(news.simHash);
                                } else {
                                    CopyOnWriteArrayList<Long> newBucket = new CopyOnWriteArrayList<>();
                                    newBucket.add(news.simHash);
                                    CopyOnWriteArrayList<Long> existing = simHashSeen.putIfAbsent(key, newBucket);
                                    if (existing != null) {
                                        existing.add(news.simHash);
                                    }
                                }
                            }

                            totalNew.incrementAndGet();

                            // Submit to format pool: format → persist in background thread
                            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                try {
                                    // Persist raw news first, then format
                                    persistAndFormat(news, report);
                                    int done = formattedCount.incrementAndGet();
                                    emitProgress(taskId, "progress", new CombinedProgress(
                                        new PullingProgress(true, "", totalSources + "/" + totalSources, totalNew.get()),
                                        new FormattingProgress(false, done, totalNew.get(), "格式化中")
                                    ));
                                } catch (Exception e) {
                                    Log.debugf("Task %d: Format failed for news: %s", taskId, e.getMessage());
                                    formattedCount.incrementAndGet();
                                }
                            }, formatPool);
                            formatFutures.add(future);

                        } catch (Exception e) {
                            Log.debugf("Task %d: Skip entry from %s: %s", taskId, source.name, e.getMessage());
                        }
                    }

                    Log.infof("Task %d: Source %s done — %d entries, %d new submitted",
                        taskId, source.name, entries.size(),
                        totalFetched.get() /* approx for this source */);

                } catch (Exception e) {
                    String msg = source.name + ": " + e.getMessage();
                    errors.add(msg);
                    Log.errorf("Task %d: Source fetch failed: %s", taskId, msg);
                }
            }

            // Wait for all format tasks to complete
            int pending = formatFutures.size();
            if (pending > 0) {
                Log.infof("Task %d: Waiting for %d format tasks to complete...", taskId, pending);
                CompletableFuture.allOf(formatFutures.toArray(new CompletableFuture[0]))
                    .get(300, TimeUnit.SECONDS);
            }

            // Emit formatting completion (markTaskCompleted handled by caller)
            emitProgress(taskId, "progress", new CombinedProgress(
                new PullingProgress(true, "", totalSources + "/" + totalSources, totalNew.get()),
                new FormattingProgress(true, formattedCount.get(), totalNew.get(), "格式化完成")
            ));

        } finally {
            formatPool.shutdown();
            try { formatPool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Persist + format news in a single transaction.
     * Runs in a format pool thread.
     */
    void persistAndFormat(News news, Report report) {
        // formatOneNews does persist + full pipeline in its own @Transactional
        newsFormatService.formatOneNews(news, report);
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

}
