package org.rsscount.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 系统设置实体 — 全局唯一的配置行，存储定时任务、AI 等基础参数。
 * 通过 {@link #getOrCreate()} 获取或创建单例行。
 */
@Entity
@Table(name = "settings")
public class Settings extends PanacheEntityBase {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 定时拉取任务的间隔小时数，默认 6 小时 */
    @Column(name = "task_interval_hours", nullable = false)
    public int taskIntervalHours = 6;

    /** AI API 地址，最长 500 字符 */
    @Column(name = "ai_api_url", length = 500)
    public String aiApiUrl;

    /** AI API 密钥，最长 200 字符 */
    @Column(name = "ai_api_key", length = 200)
    public String aiApiKey;

    /** AI 模型名称，最长 100 字符 */
    @Column(name = "ai_model", length = 100)
    public String aiModel;

    /** 默认关联的 RSS 分组（多对一，懒加载；分组删除时置为 NULL） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_group_id",
        foreignKey = @ForeignKey(foreignKeyDefinition =
            "FOREIGN KEY (default_group_id) REFERENCES rss_group(id) ON DELETE SET NULL"))
    public RssGroup defaultGroup;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    /** 全局单例锁 */
    private static final Object LOCK = new Object();

    /** 单例标识列，始终为 1，由唯一约束强制全表只有一行 */
    @Column(name = "singleton", nullable = false, unique = true)
    private int singleton = 1;

    /**
     * 获取或创建全局唯一的设置行。
     * 若数据库为空则创建新实例并持久化。
     */
    public static Settings getOrCreate() {
        Settings settings = (Settings) findAll().firstResult();
        if (settings == null) {
            synchronized (LOCK) {
                settings = (Settings) findAll().firstResult();
                if (settings == null) {
                    settings = new Settings();
                    settings.persistAndFlush();
                }
            }
        }
        return settings;
    }
}
