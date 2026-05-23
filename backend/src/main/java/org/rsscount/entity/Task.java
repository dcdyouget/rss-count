package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务实体 — 定义一次 RSS 拉取/报告生成任务的范围与状态。
 * 任务按时间窗口从 RSS 源拉取新闻并生成报告。
 */
@Entity
@Table(name = "task", indexes = {
    @Index(name = "idx_task_status", columnList = "status"),
    @Index(name = "idx_task_created_at", columnList = "created_at DESC")
})
public class Task extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 任务名称，最长 200 字符 */
    @Column(name = "name", length = 200, nullable = false)
    public String name;

    /** 时间窗口起始时间（包含），用于筛选新闻 */
    @Column(name = "time_range_start", nullable = false)
    public LocalDateTime timeRangeStart;

    /** 时间窗口结束时间（包含），用于筛选新闻 */
    @Column(name = "time_range_end", nullable = false)
    public LocalDateTime timeRangeEnd;

    /** 任务状态：RUNNING / COMPLETED / FAILED，默认为 RUNNING */
    @Column(name = "status", length = 20, nullable = false)
    public String status = "RUNNING";

    /** 来源类型：ALL / GROUP / SOURCE / MIXED */
    @Column(name = "source_type", length = 10, nullable = false)
    public String sourceType;

    /** 来源配置 JSON（根据 source_type 存储对应参数） */
    @Column(name = "source_config", columnDefinition = "TEXT")
    public String sourceConfig;

    /** 记录创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** 任务开始执行时间 */
    @Column(name = "started_at")
    public LocalDateTime startedAt;

    /** 任务结束时间 */
    @Column(name = "ended_at")
    public LocalDateTime endedAt;

    /** 失败时的错误信息，最长 2000 字符 */
    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    // === 任务状态常量 ===
    /** 任务状态：运行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 任务状态：已完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 任务状态：失败 */
    public static final String STATUS_FAILED = "FAILED";

    // === 来源类型常量 ===
    /** 来源类型：全部 RSS 源 */
    public static final String SOURCE_ALL = "ALL";
    /** 来源类型：按分组拉取 */
    public static final String SOURCE_GROUP = "GROUP";
    /** 来源类型：按单个源拉取 */
    public static final String SOURCE_SOURCE = "SOURCE";
    /** 来源类型：混合模式（取所有未分组源） */
    public static final String SOURCE_MIXED = "MIXED";
}
