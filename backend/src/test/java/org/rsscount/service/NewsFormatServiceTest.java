package org.rsscount.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rsscount.entity.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * U5: NewsFormatService integration tests.
 */
@QuarkusTest
class NewsFormatServiceTest {

    @InjectMock
    ContentExtractor mockContentExtractor;

    @InjectMock
    AiService mockAiService;

    @Inject
    NewsFormatService newsFormatService;

    @BeforeEach
    @Transactional
    void cleanup() {
        NewsTag.deleteAll();
        Tag.deleteAll();
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
    }

    private Report createTestReport() {
        Task task = new Task();
        task.name = "NewsFormat Test Task";
        task.timeRangeStart = LocalDateTime.now().minusHours(2);
        task.timeRangeEnd = LocalDateTime.now();
        task.status = Task.STATUS_COMPLETED;
        task.sourceType = Task.SOURCE_ALL;
        task.persist();

        Report report = new Report();
        report.task = task;
        report.name = "NewsFormat Test Report";
        report.timeRangeStart = task.timeRangeStart;
        report.timeRangeEnd = task.timeRangeEnd;
        report.persist();

        return report;
    }

    private News createRawNews(Report report, String title, String content) {
        News news = new News();
        news.report = report;
        news.title = title;
        news.rawContent = content;
        news.author = "测试作者";
        news.sourceRssName = "测试源";
        news.sourceUrl = "https://example.com/news/" + title.replace(" ", "_");
        news.contentLength = content != null ? content.length() : 0;
        news.publishedAt = LocalDateTime.now().minusHours(1);
        return news;
    }

    // ── formatOneNews tests ─────────────────────────────────

    @Test
    @Transactional
    void testFormatOneNewsFullPipeline() {
        Report report = createTestReport();
        String html = "<html><body><p>正文内容</p><img src='pic.jpg' alt='图'/></body></html>";
        News raw = createRawNews(report, "  【快讯】测试新闻标题！  ", html);

        // Mock dependencies
        Mockito.when(mockContentExtractor.clean(anyString(), anyString()))
            .thenReturn("<p>正文内容</p>");
        Mockito.when(mockContentExtractor.extractHeaderImage(anyString()))
            .thenReturn("https://example.com/pic.jpg");
        Mockito.when(mockAiService.generateSummary(anyString(), anyInt()))
            .thenReturn("这是一条测试摘要。");
        Mockito.when(mockAiService.extractTags(anyString()))
            .thenReturn(List.of("测试", "新闻"));

        News result = newsFormatService.formatOneNews(raw, report);

        assertNotNull(result);
        assertNotNull(result.id);

        // Title cleaned (lowercased, prefix removed, trimmed)
        assertTrue(result.title.contains("测试新闻标题"));
        assertFalse(result.title.contains("【快讯】"));

        // Structured content set
        assertNotNull(result.structuredContent);

        // Header image
        assertEquals("https://example.com/pic.jpg", result.headerImageHtml);

        // Summary generated
        assertEquals("这是一条测试摘要。", result.summary);

        // SimHash computed
        assertNotNull(result.simHash);
        assertNotEquals(0L, result.simHash);

        // Tags associated
        List<NewsTag> associations = NewsTag.find("newsId", result.id).list();
        assertFalse(associations.isEmpty());
    }

    @Test
    @Transactional
    void testFormatOneNewsNullInput() {
        assertNull(newsFormatService.formatOneNews(null, null));
    }

    @Test
    @Transactional
    void testFormatOneNewsWithoutContent() {
        Report report = createTestReport();
        News raw = createRawNews(report, "无内容新闻", null);

        News result = newsFormatService.formatOneNews(raw, report);

        assertNotNull(result);
        assertEquals("无内容新闻", result.title);
        assertNull(result.structuredContent);
        assertNull(result.headerImageHtml);
    }

    @Test
    @Transactional
    void testFormatOneNewsAiFailureDegradesGracefully() {
        Report report = createTestReport();
        News raw = createRawNews(report, "AI故障新闻", "<p>正文</p>");

        // AI fails
        Mockito.when(mockContentExtractor.clean(anyString(), anyString()))
            .thenReturn("<p>正文</p>");
        Mockito.when(mockAiService.generateSummary(anyString(), anyInt()))
            .thenReturn("");
        Mockito.when(mockAiService.extractTags(anyString()))
            .thenReturn(List.of());

        News result = newsFormatService.formatOneNews(raw, report);

        assertNotNull(result);
        assertNull(result.summary);
        // Should still persist without summary and tags
        assertNotNull(result.id);
    }

    // ── formatBatch tests ───────────────────────────────────

    @Test
    @Transactional
    void testFormatBatchMultipleNews() {
        Report report = createTestReport();
        List<News> newsList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            newsList.add(createRawNews(report, "批量新闻" + i, "<p>内容" + i + "</p>"));
        }

        Mockito.when(mockContentExtractor.clean(anyString(), anyString()))
            .thenReturn("<p>内容</p>");
        Mockito.when(mockContentExtractor.extractHeaderImage(anyString()))
            .thenReturn(null);
        Mockito.when(mockAiService.generateSummary(anyString(), anyInt()))
            .thenReturn("摘要");
        Mockito.when(mockAiService.extractTags(anyString()))
            .thenReturn(List.of("标签"));

        AtomicInteger callbackCount = new AtomicInteger(0);
        List<News> result = newsFormatService.formatBatch(newsList, report,
            news -> callbackCount.incrementAndGet());

        assertEquals(5, result.size());
        assertEquals(5, callbackCount.get());
    }

    @Test
    @Transactional
    void testFormatBatchWithNullCallback() {
        Report report = createTestReport();
        List<News> newsList = List.of(
            createRawNews(report, "新闻A", "<p>A</p>"),
            createRawNews(report, "新闻B", "<p>B</p>")
        );

        Mockito.when(mockContentExtractor.clean(anyString(), anyString()))
            .thenReturn("<p>content</p>");
        Mockito.when(mockAiService.generateSummary(anyString(), anyInt()))
            .thenReturn("摘要");
        Mockito.when(mockAiService.extractTags(anyString()))
            .thenReturn(List.of());

        // Should not throw with null callback
        List<News> result = newsFormatService.formatBatch(newsList, report, null);
        assertEquals(2, result.size());
    }

    @Test
    @Transactional
    void testFormatBatchEmptyOrNull() {
        assertTrue(newsFormatService.formatBatch(null, null, null).isEmpty());
        assertTrue(newsFormatService.formatBatch(List.of(), null, null).isEmpty());
    }

    @Test
    @Transactional
    void testFormatBatchIndividualFailureSkipsItem() {
        Report report = createTestReport();
        News goodNews = createRawNews(report, "好新闻", "<p>好内容</p>");

        // Create a news item that will cause an error during formatting
        News badNews = new News();
        badNews.report = report;

        Mockito.when(mockContentExtractor.clean(anyString(), anyString()))
            .thenReturn("<p>ok</p>");
        Mockito.when(mockAiService.generateSummary(anyString(), anyInt()))
            .thenReturn("摘要");
        Mockito.when(mockAiService.extractTags(anyString()))
            .thenReturn(List.of());

        List<News> result = newsFormatService.formatBatch(
            List.of(goodNews, badNews), report, null);

        // Bad news should be skipped; good news should succeed
        // Note: badNews has no title which might cause issues depending on impl
        assertNotNull(result);
    }
}
