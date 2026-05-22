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

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Pure RSS parsing service — no DB writes, no transactions.
 * Dedup and persistence are handled by the caller (TaskExecutor).
 */
@ApplicationScoped
public class RssFetchService {

    static final long FETCH_TIMEOUT_SECONDS = 30;

    public record FetchResult(SyndFeed feed, String etag, String lastModified) {}

    /**
     * Parse an RSS feed from an RssSource, supporting ETag/If-Modified-Since.
     */
    public FetchResult parseFeed(RssSource source) throws Exception {
        Log.infof("Fetching RSS: %s", source.url);
        URL feedUrl = new URL(source.url);
        HttpURLConnection conn = (HttpURLConnection) feedUrl.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        // 发送缓存的 ETag/Last-Modified
        if (source.etag != null && !source.etag.isBlank())
            conn.setRequestProperty("If-None-Match", source.etag);
        if (source.lastModified != null && !source.lastModified.isBlank())
            conn.setRequestProperty("If-Modified-Since", source.lastModified);

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            Log.infof("RSS not modified (304): %s", source.url);
            return new FetchResult(null, source.etag, source.lastModified);
        }

        String newEtag = conn.getHeaderField("ETag");
        String newLastModified = conn.getHeaderField("Last-Modified");

        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(conn.getInputStream()));
        Log.infof("RSS fetched: %s — %d entries", source.url, feed.getEntries().size());
        return new FetchResult(feed, newEtag, newLastModified);
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
        if (description != null && description.getValue() != null && !description.getValue().isBlank()) {
            news.summary = description.getValue();
        }

        // Content — prefer content:encoded, fallback to description
        String rawContent = null;
        List<SyndContent> contents = entry.getContents();
        if (contents != null && !contents.isEmpty()) {
            rawContent = contents.get(0).getValue();
        } else if (description != null && description.getValue() != null && !description.getValue().isBlank()) {
            rawContent = description.getValue();
        }
        news.rawContent = rawContent;
        news.contentLength = (rawContent != null) ? rawContent.length() : 0;

        // Author
        String author = entry.getAuthor();
        news.author = (author != null && !author.isBlank()) ? author : "未知";

        // Published date — normalize to UTC+8, fallback to now
        if (entry.getPublishedDate() != null) {
            news.publishedAt = entry.getPublishedDate().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime();
        } else {
            news.publishedAt = LocalDateTime.now();
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
