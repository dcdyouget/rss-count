package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "draft_version",
    uniqueConstraints = @UniqueConstraint(columnNames = {"draft_id", "version"}))
public class DraftVersion extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    public Draft draft;

    @Column(name = "version", nullable = false)
    public int version;

    @Column(name = "content", columnDefinition = "TEXT")
    public String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
