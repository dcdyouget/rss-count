package org.rsscount.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rsscount.entity.*;
import org.rsscount.util.SimHash;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * U6: TaskExecutor integration tests.
 *
 * U6-1: Full pipeline — fetch + format → COMPLETED.
 * U6-2: All sources fail → FAILED.
 * U6-3: SSE events emitted during execution.
 */
@QuarkusTest
class TaskExecutorTest {

    @InjectMock
    RssFetchService mockRssFetchService;

    @InjectMock
    NewsFormatService mockNewsFormatService;

    @Inject
    TaskExecutor taskExecutor;

    @BeforeEach
    @Transactional
    void cleanup() {
        NewsTag.deleteAll();
        Tag.deleteAll();
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    private RssSource createTestSource(String name) {
        RssSource source = new RssSource();
        source.name = name;
        source.url = "https://" + name.toLowerCase() + ".com/feed";
        source.isActive = true;
        source.persist();
        return source;
    }

    private Task createTestTask() {
        Task task = new Task();
        task.name = "测试任务";
        task.timeRangeStart = LocalDateTime.now().minusHours(2);
        task.timeRangeEnd = LocalDateTime.now();
        task.status = Task.STATUS_RUNNING;
        task.sourceType = Task.SOURCE_ALL;
        task.startedAt = LocalDateTime.now();
        task.createdAt = LocalDateTime.now();
        task.persist();
        return task;
    }

    // ── U6-1: Full pipeline ────────────────────────────────

    @Test
    @Transactional
    void testFullPipelineSuccess() throws Exception {
        // Create sources
        RssSource source1 = createTestSource("源A");
        RssSource source2 = createTestSource("源B");

        Task task = createTestTask();

        // Mock RssFetchService
        Mockito.when(mockRssFetchService.fetchFromSource(any(RssSource.class), any(Task.class), any(Report.class)))
            .thenAnswer(invocation -> {
                Report report = invocation.getArgument(2);
                // Create a news during fetch
                News news = new News();
                news.report = report;
                news.title = "测试新闻";
                news.author = "作者";
                news.sourceRssName = ((RssSource) invocation.getArgument(0)).name;
                news.sourceUrl = "https://example.com/news/1";
                news.contentLength = 100;
                news.createdAt = LocalDateTime.now();
                news.persist();
                return 1;
            });

        // Mock NewsFormatService
        Mockito.when(mockNewsFormatService.formatOneNews(any(News.class), any(Report.class)))
            .thenAnswer(invocation -> {
                News news = invocation.getArgument(0);
                news.structuredContent = "[{\"type\":\"paragraph\",\"text\":\"格式化内容\"}]";
                news.simHash = SimHash.compute(news.title);
                news.summary = "AI摘要";
                return news;
            });

        // Subscribe to SSE events
        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicReference<String> terminalEvent = new AtomicReference<>();

        taskExecutor.subscribe(task.id, event -> {
            if ("complete".equals(event.event())) {
                terminalEvent.set("complete");
                completeLatch.countDown();
            } else if ("error".equals(event.event())) {
                terminalEvent.set("error");
                completeLatch.countDown();
            }
        });

        // Execute
        taskExecutor.execute(task);

        // Wait for completion
        boolean completed = completeLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Task should complete within timeout");
        assertEquals("complete", terminalEvent.get());

        // Verify task state
        Task updated = Task.findById(task.id);
        assertNotNull(updated);
        assertEquals(Task.STATUS_COMPLETED, updated.status);
        assertNotNull(updated.endedAt);
    }

    // ── U6-2: All sources fail ─────────────────────────────

    @Test
    @Transactional
    void testAllSourcesFail() throws Exception {
        createTestSource("故障源A");
        createTestSource("故障源B");
        Task task = createTestTask();

        // Mock RssFetchService to simulate RssSource fetch failure
        Mockito.when(mockRssFetchService.fetchFromSource(any(RssSource.class), any(Task.class), any(Report.class)))
            .thenThrow(new RuntimeException("连接超时"));

        // SSE subscription
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> terminalEvent = new AtomicReference<>();

        taskExecutor.subscribe(task.id, event -> {
            if ("error".equals(event.event())) {
                terminalEvent.set("error");
                errorLatch.countDown();
            } else if ("complete".equals(event.event())) {
                terminalEvent.set("complete");
                errorLatch.countDown();
            }
        });

        taskExecutor.execute(task);

        boolean errored = errorLatch.await(10, TimeUnit.SECONDS);
        assertTrue(errored, "Task should fail within timeout");
        assertEquals("error", terminalEvent.get());

        // Verify task state
        Task updated = Task.findById(task.id);
        assertNotNull(updated);
        assertEquals(Task.STATUS_FAILED, updated.status);
        assertNotNull(updated.errorMessage);
    }

    // ── U6-3: SSE events emitted ───────────────────────────

    @Test
    @Transactional
    void testSseEventsProgress() throws Exception {
        createTestSource("SSE源");
        Task task = createTestTask();

        Mockito.when(mockRssFetchService.fetchFromSource(any(RssSource.class), any(Task.class), any(Report.class)))
            .thenAnswer(invocation -> {
                News news = new News();
                news.report = invocation.getArgument(2);
                news.title = "SSE测试新闻";
                news.author = "作者";
                news.sourceRssName = "SSE源";
                news.sourceUrl = "https://example.com/sse";
                news.contentLength = 50;
                news.createdAt = LocalDateTime.now();
                news.persist();
                return 1;
            });

        Mockito.when(mockNewsFormatService.formatOneNews(any(News.class), any(Report.class)))
            .thenAnswer(invocation -> {
                News n = invocation.getArgument(0);
                n.structuredContent = "[]";
                return n;
            });

        CountDownLatch progressLatch = new CountDownLatch(1);
        CountDownLatch terminalLatch = new CountDownLatch(1);
        AtomicReference<String> lastEvent = new AtomicReference<>();

        taskExecutor.subscribe(task.id, event -> {
            lastEvent.set(event.event());
            if ("progress".equals(event.event())) {
                progressLatch.countDown();
            } else if ("complete".equals(event.event())) {
                terminalLatch.countDown();
            }
        });

        taskExecutor.execute(task);

        // Wait for progress event
        boolean progressSeen = progressLatch.await(10, TimeUnit.SECONDS);
        assertTrue(progressSeen, "Progress event should be emitted");

        // Wait for completion
        boolean terminated = terminalLatch.await(10, TimeUnit.SECONDS);
        assertTrue(terminated, "Terminal event should be emitted");
        assertEquals("complete", lastEvent.get());
    }

    @Test
    void testSubscribeUnsubscribe() {
        Task task = createTestTask();

        java.util.function.Consumer<TaskExecutor.SseProgressEvent> callback = event -> {};
        taskExecutor.subscribe(task.id, callback);

        // Should not throw
        taskExecutor.unsubscribe(task.id, callback);

        // Unsubscribe again should be no-op
        taskExecutor.unsubscribe(task.id, callback);
    }

    @Test
    void testIsRunning() {
        Task task = createTestTask();
        assertFalse(taskExecutor.isRunning(task.id));
        // After execute starts, running state will be set
    }

    @Test
    void testToJsonSafely() {
        String json = taskExecutor.toJsonSafely(new TaskExecutor.CompleteData(42L));
        assertTrue(json.contains("\"reportId\""));
        assertTrue(json.contains("42"));
    }

    @Test
    void testToJsonSafelyWithNull() {
        String json = taskExecutor.toJsonSafely(null);
        assertEquals("{}", json);
    }
}
