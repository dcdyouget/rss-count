package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rss_source")
public class RssSource extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "url", length = 2048, nullable = false, unique = true)
    public String url;

    @Column(name = "name", length = 200, nullable = false)
    public String name;

    @Column(name = "icon_path", length = 500)
    public String iconPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_fetch_at")
    public LocalDateTime lastFetchAt;

    @Column(name = "etag", length = 512)
    public String etag;

    @Column(name = "last_modified", length = 512)
    public String lastModified;

    @Column(name = "total_fetched", nullable = false)
    public int totalFetched = 0;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
