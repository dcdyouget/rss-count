package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 稿件版本实体 — 记录稿件每次修改的历史快照。
 * 同一稿件下版本号唯一且递增。
 */
@Entity
@Table(name = "draft_version",
    uniqueConstraints = @UniqueConstraint(columnNames = {"draft_id", "version"}))
public class DraftVersion extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 所属稿件（多对一，懒加载） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    public Draft draft;

    /** 版本号，与 draft_id 联合唯一 */
    @Column(name = "version", nullable = false)
    public int version;

    /** 该版本的稿件内容文本 */
    @Column(name = "content", columnDefinition = "TEXT")
    public String content;

    /** 版本创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** 该版本使用的提示词（prompt），快照历史生成参数 */
    @Column(name = "prompt", columnDefinition = "TEXT")
    public String prompt;

    /** 该版本使用的 AI 温度参数 */
    @Column(name = "temperature")
    public Double temperature;

    /** 该版本使用的写作风格 */
    @Column(name = "style", length = 50)
    public String style;

    /** 该版本的目标发布平台 */
    @Column(name = "target_platform", length = 50)
    public String targetPlatform;
}
