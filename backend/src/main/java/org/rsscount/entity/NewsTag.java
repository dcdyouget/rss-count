package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * 新闻-标签关联实体 — 实现新闻与标签的多对多关系。
 * 联合主键由 newsId 和 tagId 组成。
 */
@Entity
@Table(name = "news_tag")
@IdClass(NewsTagId.class)
public class NewsTag extends PanacheEntityBase {

    /** 新闻 ID（联合主键的一部分） */
    @Id
    @Column(name = "news_id", nullable = false)
    public Long newsId;

    /** 标签 ID（联合主键的一部分） */
    @Id
    @Column(name = "tag_id", nullable = false)
    public Long tagId;
}
