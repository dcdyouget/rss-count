package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rss_group")
public class RssGroup extends PanacheEntity {

    @Column(name = "name", length = 100, nullable = false, unique = true)
    public String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
