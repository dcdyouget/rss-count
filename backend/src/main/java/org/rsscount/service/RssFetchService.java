package org.rsscount.service;

import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.rsscount.entity.News;
import org.rsscount.entity.Report;
import org.rsscount.entity.RssSource;
import org.rsscount.util.SimHash;
import org.rsscount.util.TextCleaner;

import java.net.URL;
import java.time.ZoneId;
import java.util.List;

/**
 * Pure RSS parsing service — no DB writes, no transactions.
 * Dedup and persistence are handled by the caller (TaskExecutor).
 */
@ApplicationScoped
public class RssFetchService {

    static final long FETCH_TIMEOUT_SECONDS = 30;

    /**
     * Parse an RSS feed URL into a SyndFeed.
     */
    public SyndFeed parseFeed(String url) throws Exception {
        URL feedUrl = new URL(url);
        java.net.URLConnection conn = feedUrl.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        SyndFeedInput input = new SyndFeedInput();
        return input.build(new XmlReader(conn.getInputStream()));
    }

    /**
     * Convert a ROME SyndEntry to a raw News entity (NOT persisted).
     * SimHash is computed from title + first 500 chars of content.
     */
    public News syndEntryToNews(SyndEntry entry, RssSource source, Report report) {
        News news = new News();
        news.report = report;
        news.sourceRss = source;
        news.sourceRssName = source.name;

        // Title — store raw, cleaning happens later in format phase
        String rawTitle = entry.getTitle();
        news.title = (rawTitle != null && !rawTitle.isBlank()) ? rawTitle : "";

        // Link
        news.sourceUrl = entry.getLink();

        // Summary — prefer description
        SyndContent description = entry.getDescription();
        if (description != null && description.getValue() != null) {
            news.summary = description.getValue();
        }

        // Content — prefer content:encoded, fallback to description
        String rawContent = null;
        List<SyndContent> contents = entry.getContents();
        if (contents != null && !contents.isEmpty()) {
            rawContent = contents.get(0).getValue();
        } else if (description != null && description.getValue() != null) {
            rawContent = description.getValue();
        }
        news.rawContent = rawContent;
        news.contentLength = (rawContent != null) ? rawContent.length() : 0;

        // Author
        String author = entry.getAuthor();
        news.author = (author != null && !author.isBlank()) ? author : "未知";

        // Published date — normalize to UTC+8
        if (entry.getPublishedDate() != null) {
            news.publishedAt = entry.getPublishedDate().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime();
        }

        // Category
        List<SyndCategory> categories = entry.getCategories();
        if (categories != null && !categories.isEmpty()) {
            news.category = categories.get(0).getName();
        }

        // SimHash — from cleaned title + first 500 chars of content
        String cleanTitle = TextCleaner.clean(news.title);
        StringBuilder simHashInput = new StringBuilder(cleanTitle);
        if (rawContent != null && !rawContent.isEmpty()) {
            simHashInput.append(' ');
            int limit = Math.min(500, rawContent.length());
            simHashInput.append(rawContent, 0, limit);
        }
        news.simHash = SimHash.compute(simHashInput.toString());

        return news;
    }
}
