package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "news_tag")
@IdClass(NewsTagId.class)
public class NewsTag extends PanacheEntityBase {

    @Id
    @Column(name = "news_id", nullable = false)
    public Long newsId;

    @Id
    @Column(name = "tag_id", nullable = false)
    public Long tagId;
}
