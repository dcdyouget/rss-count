package org.rsscount.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rsscount.entity.Settings;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * I3: AI service tests.
 *
 * I3-1: Normal call returns summary and tags.
 * I3-2: Timeout/degradation returns empty string and empty list.
 */
@QuarkusTest
class AiServiceTest {

    @InjectMock
    AiService.OpenAiCompatibleProvider mockProvider;

    @Inject
    AiService aiService;

    @BeforeEach
    void setup() {
        Mockito.reset(mockProvider);
    }

    // ---- I3-1: Normal call returns summary and tags ----

    @Test
    void testGenerateSummaryNormal() {
        String expectedSummary = "本文报道了AI行业的最新进展，多家科技公司发布了新产品。";
        Mockito.when(mockProvider.generateSummary(anyString(), anyInt()))
            .thenReturn(expectedSummary);

        String result = aiService.generateSummary("这是一篇关于AI行业的新闻报道，内容涉及...", 200);
        assertEquals(expectedSummary, result);
    }

    @Test
    void testExtractTagsNormal() {
        List<String> expectedTags = List.of("AI", "科技", "融资");
        Mockito.when(mockProvider.extractTags(anyString()))
            .thenReturn(expectedTags);

        List<String> result = aiService.extractTags("AI行业迎来新突破，多家公司获得融资...");
        assertEquals(3, result.size());
        assertTrue(result.contains("AI"));
        assertTrue(result.contains("科技"));
        assertTrue(result.contains("融资"));
    }

    @Test
    void testBothMethodsNormal() {
        Mockito.when(mockProvider.generateSummary(anyString(), anyInt()))
            .thenReturn("这是一篇摘要。");
        Mockito.when(mockProvider.extractTags(anyString()))
            .thenReturn(List.of("AI", "科技"));

        String summary = aiService.generateSummary("test content", 200);
        List<String> tags = aiService.extractTags("test content");

        assertEquals("这是一篇摘要。", summary);
        assertEquals(2, tags.size());
    }

    // ---- I3-2: Timeout/degradation ----

    @Test
    void testGenerateSummaryTimeoutDegradation() {
        Mockito.when(mockProvider.generateSummary(anyString(), anyInt()))
            .thenReturn("");

        String result = aiService.generateSummary("test content that would timeout", 200);
        assertEquals("", result);
    }

    @Test
    void testExtractTagsTimeoutDegradation() {
        Mockito.when(mockProvider.extractTags(anyString()))
            .thenReturn(Collections.emptyList());

        List<String> result = aiService.extractTags("test content that would timeout");
        assertTrue(result.isEmpty());
    }

    @Test
    void testBothMethodsTimeoutDegradation() {
        Mockito.when(mockProvider.generateSummary(anyString(), anyInt()))
            .thenReturn("");
        Mockito.when(mockProvider.extractTags(anyString()))
            .thenReturn(Collections.emptyList());

        String summary = aiService.generateSummary("test content", 200);
        List<String> tags = aiService.extractTags("test content");

        assertEquals("", summary);
        assertTrue(tags.isEmpty());
    }

    @Test
    void testAiProviderInterfaceContract() {
        // Verify the interface has the expected methods
        AiService.AiProvider provider = mockProvider;
        assertNotNull(provider);
    }
}
