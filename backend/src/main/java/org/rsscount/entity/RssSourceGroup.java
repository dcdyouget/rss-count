package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * RSS 源-分组关联实体 — 实现 RSS 源与分组的多对多关系。
 * 联合主键由 rssSourceId 和 rssGroupId 组成。
 */
@Entity
@Table(name = "rss_source_group")
@IdClass(RssSourceGroupId.class)
public class RssSourceGroup extends PanacheEntityBase {

    /** RSS 源 ID（联合主键的一部分） */
    @Id
    @Column(name = "rss_source_id", nullable = false)
    public Long rssSourceId;

    /** RSS 分组 ID（联合主键的一部分） */
    @Id
    @Column(name = "rss_group_id", nullable = false)
    public Long rssGroupId;
}
