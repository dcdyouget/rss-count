package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "news", indexes = {
    @Index(name = "idx_news_report_id", columnList = "report_id"),
    @Index(name = "idx_news_title", columnList = "title"),
    @Index(name = "idx_news_sim_hash", columnList = "sim_hash"),
    @Index(name = "idx_news_created_at", columnList = "created_at DESC")
})
public class News extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    public Report report;

    @Column(name = "title", length = 500, nullable = false)
    public String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    public String summary;

    @Column(name = "author", length = 100, nullable = false)
    public String author = "未知";

    @Column(name = "raw_content", columnDefinition = "TEXT")
    public String rawContent;

    @Column(name = "structured_content", columnDefinition = "TEXT")
    public String structuredContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_rss_id",
        foreignKey = @ForeignKey(foreignKeyDefinition =
            "FOREIGN KEY (source_rss_id) REFERENCES rss_source(id) ON DELETE SET NULL"))
    public RssSource sourceRss;

    @Column(name = "source_rss_name", length = 200)
    public String sourceRssName;

    @Column(name = "source_url", length = 2048)
    public String sourceUrl;

    @Column(name = "header_image_url", length = 2048)
    public String headerImageUrl;

    @Column(name = "category", length = 50)
    public String category;

    @Column(name = "published_at")
    public LocalDateTime publishedAt;

    @Column(name = "sim_hash")
    public Long simHash;

    @Column(name = "content_length", nullable = false)
    public int contentLength = 0;

    @Column(name = "is_read", nullable = false)
    public boolean isRead = false;

    @Column(name = "in_material_pile", nullable = false)
    public boolean inMaterialPile = false;

    @Column(name = "material_pile_added_at")
    public LocalDateTime materialPileAddedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
