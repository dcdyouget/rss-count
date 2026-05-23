package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 标签实体 — 新闻分类标签。
 * 标签名称全局唯一，通过 NewsTag 中间表与新闻多对多关联。
 */
@Entity
@Table(name = "tag")
public class Tag extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 标签名称，全局唯一，最长 50 字符 */
    @Column(name = "name", length = 50, nullable = false, unique = true)
    public String name;

    /** 创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
