package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "draft")
public class Draft extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", length = 200, nullable = false)
    public String name;

    @Column(name = "prompt", columnDefinition = "TEXT")
    public String prompt;

    @Column(name = "temperature", nullable = false)
    public double temperature = 0.7;

    @Column(name = "style", length = 50)
    public String style;

    @Column(name = "target_platform", length = 50)
    public String targetPlatform;

    @Column(name = "latest_version", nullable = false)
    public int latestVersion = 0;

    @Column(name = "latest_content", columnDefinition = "TEXT")
    public String latestContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();
}
