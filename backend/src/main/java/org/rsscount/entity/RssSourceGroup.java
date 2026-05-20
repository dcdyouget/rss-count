package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "rss_source_group")
@IdClass(RssSourceGroupId.class)
public class RssSourceGroup extends PanacheEntityBase {

    @Id
    @Column(name = "rss_source_id", nullable = false)
    public Long rssSourceId;

    @Id
    @Column(name = "rss_group_id", nullable = false)
    public Long rssGroupId;
}
