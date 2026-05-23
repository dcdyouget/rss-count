package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * RSS 源实体 — 记录订阅的 RSS/Atom  feed。
 * 每个源包含 URL、名称和抓取状态。
 */
@Entity
@Table(name = "rss_source")
public class RssSource extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** RSS feed URL，全局唯一，最长 2048 字符 */
    @Column(name = "url", length = 2048, nullable = false, unique = true)
    public String url;

    /** RSS 源名称，最长 200 字符 */
    @Column(name = "name", length = 200, nullable = false)
    public String name;

    /** 图标存储路径，最长 500 字符 */
    @Column(name = "icon_path", length = 500)
    public String iconPath;

    /** 创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** 最近一次成功抓取时间 */
    @Column(name = "last_fetch_at")
    public LocalDateTime lastFetchAt;

    /** HTTP ETag 缓存头，用于条件抓取，最长 512 字符 */
    @Column(name = "etag", length = 512)
    public String etag;

    /** HTTP Last-Modified 缓存头，用于条件抓取，最长 512 字符 */
    @Column(name = "last_modified", length = 512)
    public String lastModified;

    /** 累计抓取文章总数 */
    @Column(name = "total_fetched", nullable = false)
    public int totalFetched = 0;

    /** 是否启用，默认启用；禁用后不再自动抓取 */
    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
