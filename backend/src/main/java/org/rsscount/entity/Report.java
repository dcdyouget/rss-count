package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 报告实体 — 一次任务执行结果的产出物。
 * 报告包含时间窗口内的新闻集合摘要信息。
 */
@Entity
@Table(name = "report", indexes = {
    @Index(name = "idx_report_created_at", columnList = "created_at DESC")
})
public class Report extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 关联的生成任务（一对一，懒加载），每个任务唯一对应一份报告 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    public Task task;

    /** 报告名称，最长 200 字符 */
    @Column(name = "name", length = 200, nullable = false)
    public String name;

    /** 报告涵盖的时间窗口起始时间 */
    @Column(name = "time_range_start", nullable = false)
    public LocalDateTime timeRangeStart;

    /** 报告涵盖的时间窗口结束时间 */
    @Column(name = "time_range_end", nullable = false)
    public LocalDateTime timeRangeEnd;

    /** 报告包含的新闻数量 */
    @Column(name = "news_count", nullable = false)
    public int newsCount = 0;

    /** 报告创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
