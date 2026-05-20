package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "settings")
public class Settings extends PanacheEntity {

    @Column(name = "task_interval_hours", nullable = false)
    public int taskIntervalHours = 6;

    @Column(name = "ai_api_url", length = 500)
    public String aiApiUrl;

    @Column(name = "ai_api_key", length = 200)
    public String aiApiKey;

    @Column(name = "ai_model", length = 100)
    public String aiModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_group_id",
        foreignKey = @ForeignKey(foreignKeyDefinition =
            "FOREIGN KEY (default_group_id) REFERENCES rss_group(id) ON DELETE SET NULL"))
    public RssGroup defaultGroup;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Get or create the singleton Settings row (id=1).
     */
    public static Settings getOrCreate() {
        Settings settings = findById(1L);
        if (settings == null) {
            settings = new Settings();
            settings.id = 1L;
            settings.persist();
        }
        return settings;
    }
}
