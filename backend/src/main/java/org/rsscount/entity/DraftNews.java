package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * 稿件-新闻关联实体 — 记录稿件使用了哪些新闻作为素材。
 * 联合主键包含 draftId 和 newsId，支持排序。
 */
@Entity
@Table(name = "draft_news")
@IdClass(DraftNewsId.class)
public class DraftNews extends PanacheEntityBase {

    /** 稿件 ID（联合主键的一部分） */
    @Id
    @Column(name = "draft_id", nullable = false)
    public Long draftId;

    /** 新闻 ID（联合主键的一部分） */
    @Id
    @Column(name = "news_id", nullable = false)
    public Long newsId;

    /** 排序序号，值越小越靠前 */
    @Column(name = "sort_order", nullable = false)
    public int sortOrder = 0;
}
