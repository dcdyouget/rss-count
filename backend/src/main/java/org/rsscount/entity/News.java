package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 新闻实体 — RSS 拉取的核心数据单元。
 * 一条新闻属于一个报告，来自一个 RSS 源，可关联多个标签。
 */
@Entity
@Table(name = "news", indexes = {
    @Index(name = "idx_news_report_id", columnList = "report_id"),
    @Index(name = "idx_news_title", columnList = "title"),
    @Index(name = "idx_news_sim_hash", columnList = "sim_hash"),
    @Index(name = "idx_news_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_news_material_pile", columnList = "in_material_pile,material_pile_added_at DESC"),
    @Index(name = "idx_news_is_read_created", columnList = "is_read,created_at DESC")
})
public class News extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 所属报告（多对一，懒加载） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    public Report report;

    /** 新闻标题（已清洗），最长 500 字符 */
    @Column(name = "title", length = 500, nullable = false)
    public String title;

    /** AI 生成的摘要概览，纯文本 */
    @Column(name = "summary", columnDefinition = "TEXT")
    public String summary;

    /** 作者名，默认为"未知" */
    @Column(name = "author", length = 100, nullable = false)
    public String author = "未知";

    /** 原始 RSS HTML 内容 */
    @Column(name = "raw_content", columnDefinition = "TEXT")
    public String rawContent;

    /** 清洗后的 HTML 结构化内容（安全标签子集，无脚本/样式，绝对 URL） */
    @Column(name = "structured_content", columnDefinition = "TEXT")
    public String structuredContent;

    /** 来源 RSS 源（多对一，懒加载；源删除时置为 NULL） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_rss_id",
        foreignKey = @ForeignKey(foreignKeyDefinition =
            "FOREIGN KEY (source_rss_id) REFERENCES rss_source(id) ON DELETE SET NULL"))
    public RssSource sourceRss;

    /** RSS 源名称（冗余字段，便于展示），最长 200 字符 */
    @Column(name = "source_rss_name", length = 200)
    public String sourceRssName;

    /** 新闻原始 URL，最长 2048 字符 */
    @Column(name = "source_url", length = 2048)
    public String sourceUrl;

    /** 头图 HTML（图片卡片嵌入用），最长 4096 字符 */
    @Column(name = "header_image_html", length = 4096)
    public String headerImageHtml;

    /** 分类标签，最长 50 字符 */
    @Column(name = "category", length = 50)
    public String category;

    /** 原文发布时间 */
    @Column(name = "published_at")
    public LocalDateTime publishedAt;

    /** SimHash 指纹，用于去重检测 */
    @Column(name = "sim_hash")
    public Long simHash;

    /** 内容长度（字符数） */
    @Column(name = "content_length", nullable = false)
    public int contentLength = 0;

    /** 是否已读 */
    @Column(name = "is_read", nullable = false)
    public boolean isRead = false;

    /** 是否已加入素材堆 */
    @Column(name = "in_material_pile", nullable = false)
    public boolean inMaterialPile = false;

    /** 加入素材堆的时间 */
    @Column(name = "material_pile_added_at")
    public LocalDateTime materialPileAddedAt;

    /** 记录创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
