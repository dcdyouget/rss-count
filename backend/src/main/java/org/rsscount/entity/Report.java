package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report", indexes = {
    @Index(name = "idx_report_created_at", columnList = "created_at DESC")
})
public class Report extends PanacheEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    public Task task;

    @Column(name = "name", length = 200, nullable = false)
    public String name;

    @Column(name = "time_range_start", nullable = false)
    public LocalDateTime timeRangeStart;

    @Column(name = "time_range_end", nullable = false)
    public LocalDateTime timeRangeEnd;

    @Column(name = "news_count", nullable = false)
    public int newsCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
