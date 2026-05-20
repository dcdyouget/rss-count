package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "draft_news")
@IdClass(DraftNewsId.class)
public class DraftNews extends PanacheEntityBase {

    @Id
    @Column(name = "draft_id", nullable = false)
    public Long draftId;

    @Id
    @Column(name = "news_id", nullable = false)
    public Long newsId;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder = 0;
}
