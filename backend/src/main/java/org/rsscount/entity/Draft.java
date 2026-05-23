package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 稿件实体 — 由 AI 生成的写作草稿。
 * 每份稿件包含生成参数（提示词、温度、风格）和版本历史。
 */
@Entity
@Table(name = "draft")
public class Draft extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 稿件名称，最长 200 字符 */
    @Column(name = "name", length = 200, nullable = false)
    public String name;

    /** AI 生成提示词（prompt），决定生成方向 */
    @Column(name = "prompt", columnDefinition = "TEXT")
    public String prompt;

    /** AI 温度参数，控制生成随机性，默认 0.7 */
    @Column(name = "temperature", nullable = false)
    public double temperature = 0.7;

    /** 写作风格，最长 50 字符（如"正式"、"轻松"） */
    @Column(name = "style", length = 50)
    public String style;

    /** 目标发布平台，最长 50 字符（如"公众号"、"博客"） */
    @Column(name = "target_platform", length = 50)
    public String targetPlatform;

    /** 最新版本号，从 0 递增 */
    @Column(name = "latest_version", nullable = false)
    public int latestVersion = 0;

    /** 最新版本的稿件内容文本 */
    @Column(name = "latest_content", columnDefinition = "TEXT")
    public String latestContent;

    /** 创建时间，不可更新 */
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();
}
