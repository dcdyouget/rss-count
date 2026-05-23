package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * RSS 分组实体 — 用于对 RSS 源进行逻辑归类。
 * 分组名称全局唯一。
 */
@Entity
@Table(name = "rss_group")
public class RssGroup extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 分组名称，全局唯一，最长 100 字符 */
    @Column(name = "name", length = 100, nullable = false, unique = true)
    public String name;

    /** 创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
