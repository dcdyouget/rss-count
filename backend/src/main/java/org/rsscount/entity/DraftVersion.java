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
}
