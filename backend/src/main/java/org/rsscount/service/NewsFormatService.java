package org.rsscount.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.rsscount.entity.*;
import org.rsscount.util.SimHash;
import org.rsscount.util.TextCleaner;
import org.rsscount.util.TimeNormalizer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * News formatting service — processes raw news through the full formatting pipeline.
 *
 * Pipeline per news:
 * 1. Clean title via TextCleaner
 * 2. Extract structured content via ContentExtractor
 * 3. Extract header image
 * 4. Normalize published time via TimeNormalizer
 * 5. Generate AI summary via AiService
 * 6. Extract AI tags via AiService
 * 7. Compute SimHash
 * 8. Persist and create NewsTag associations
 */
@ApplicationScoped
public class NewsFormatService {

    @Inject
    ContentExtractor contentExtractor;

    @Inject
    AiService aiService;

    /**
     * Format a single news item through the full pipeline.
     *
     * @param raw    the raw news entity (must be managed or persisted)
     * @param report the associated report
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public News formatOneNews(News raw, Report report) {
        if (raw == null) {
            return null;
        }

        // 1. Clean title
        if (raw.title != null) {
            raw.title = TextCleaner.clean(raw.title);
        }

        // 2. Extract structured content from raw HTML
        String rawContent = raw.rawContent;
        if (rawContent != null && !rawContent.isBlank()) {
            String structured = contentExtractor.extract(rawContent);
            raw.structuredContent = structured;
        }

        // 3. Extract header image
        if (raw.headerImageUrl == null && rawContent != null && !rawContent.isBlank()) {
            String headerImage = contentExtractor.extractHeaderImage(rawContent);
            if (headerImage != null) {
                raw.headerImageUrl = headerImage;
            }
        }

        // 4. Normalize time
        if (raw.publishedAt == null) {
            // Try to parse from raw title or other fields — already handled at fetch stage
        }

        // 5. Generate AI summary if no summary exists
        if ((raw.summary == null || raw.summary.isBlank()) && rawContent != null && !rawContent.isBlank()) {
            String contentForSummary = rawContent.length() > 1000 ? rawContent.substring(0, 1000) : rawContent;
            String aiSummary = aiService.generateSummary(contentForSummary, 200);
            if (aiSummary != null && !aiSummary.isBlank()) {
                raw.summary = aiSummary;
            }
        }

        // 6. Extract tags via AI
        String contentForTags = rawContent != null && !rawContent.isBlank()
            ? (rawContent.length() > 2000 ? rawContent.substring(0, 2000) : rawContent)
            : raw.title;
        List<String> tags = aiService.extractTags(contentForTags);
        if (tags != null && !tags.isEmpty()) {
            associateTags(raw, tags);
        }

        // 7. Compute SimHash if not already computed
        if (raw.simHash == null || raw.simHash == 0L) {
            StringBuilder simHashInput = new StringBuilder(raw.title != null ? raw.title : "");
            if (rawContent != null && !rawContent.isEmpty()) {
                simHashInput.append(' ');
                int limit = Math.min(500, rawContent.length());
                simHashInput.append(rawContent, 0, limit);
            }
            raw.simHash = SimHash.compute(simHashInput.toString());
        }

        // 8. Persist
        raw.persist();

        return raw;
    }

    /**
     * Format a batch of news items. Each news is formatted independently.
     * A callback is invoked after each item completes.
     *
     * @param newsList list of raw news to format
     * @param report   the associated report
     * @param callback progress callback, invoked with formatted news after each item
     * @return list of formatted news
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<News> formatBatch(List<News> newsList, Report report, Consumer<News> callback) {
        if (newsList == null || newsList.isEmpty()) {
            return List.of();
        }

        List<News> result = new ArrayList<>();
        for (News raw : newsList) {
            try {
                News formatted = formatOneNews(raw, report);
                if (formatted != null) {
                    result.add(formatted);
                    if (callback != null) {
                        callback.accept(formatted);
                    }
                }
            } catch (Exception e) {
                // Skip individual news formatting failures
                java.util.logging.Logger.getLogger(NewsFormatService.class.getName())
                    .warning("Format failed for news: " + (raw.title != null ? raw.title : "unknown")
                        + " — " + e.getMessage());
            }
        }

        return result;
    }

    // ── Private helpers ─────────────────────────────────────

    /**
     * Create NewsTag associations for the given tags.
     * Creates new Tag entities if they don't exist.
     */
    private void associateTags(News news, List<String> tagNames) {
        for (String tagName : tagNames) {
            if (tagName == null || tagName.isBlank()) {
                continue;
            }

            // Find or create tag
            Tag tag = Tag.find("name", tagName).firstResult();
            if (tag == null) {
                tag = new Tag();
                tag.name = tagName;
                tag.persist();
            }

            // Check if association already exists
            NewsTag existing = NewsTag.find("newsId = ?1 and tagId = ?2", news.id, tag.id)
                .firstResult();
            if (existing == null) {
                NewsTag nt = new NewsTag();
                nt.newsId = news.id;
                nt.tagId = tag.id;
                nt.persist();
            }
        }
    }
}
