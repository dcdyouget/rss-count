package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task", indexes = {
    @Index(name = "idx_task_status", columnList = "status"),
    @Index(name = "idx_task_created_at", columnList = "created_at DESC")
})
public class Task extends PanacheEntity {

    @Column(name = "name", length = 200, nullable = false)
    public String name;

    @Column(name = "time_range_start", nullable = false)
    public LocalDateTime timeRangeStart;

    @Column(name = "time_range_end", nullable = false)
    public LocalDateTime timeRangeEnd;

    @Column(name = "status", length = 20, nullable = false)
    public String status = "RUNNING";

    @Column(name = "source_type", length = 10, nullable = false)
    public String sourceType;

    @Column(name = "source_config", columnDefinition = "TEXT")
    public String sourceConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    public LocalDateTime startedAt;

    @Column(name = "ended_at")
    public LocalDateTime endedAt;

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Status constants
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // Source type constants
    public static final String SOURCE_ALL = "ALL";
    public static final String SOURCE_GROUP = "GROUP";
    public static final String SOURCE_SOURCE = "SOURCE";
    public static final String SOURCE_MIXED = "MIXED";
}
