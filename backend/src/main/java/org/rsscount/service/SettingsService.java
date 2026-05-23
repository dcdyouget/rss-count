package org.rsscount.service;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.rsscount.entity.Settings;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统设置服务 — 管理任务间隔、AI API 配置和默认分组。
 * AI API Key 在响应中自动脱敏（显示首 3 位和末 3 位）。
 */
@ApplicationScoped
public class SettingsService {

    /** 系统设置响应（API Key 已脱敏） */
    public record SettingsResponse(
        int taskIntervalHours,
        String aiApiUrl,
        String aiApiKey,        // 已脱敏
        String aiModel,
        Long defaultGroupId,
        LocalDateTime updatedAt
    ) {}

    /** 更新系统设置请求 */
    public record UpdateSettingsRequest(
        int taskIntervalHours,
        String aiApiUrl,
        String aiApiKey,
        String aiModel,
        Long defaultGroupId
    ) {}

    /**
     * 获取当前系统设置，不存在时使用默认值创建。
     * @return 系统设置响应（API Key 已脱敏）
     */
    @Transactional
    public SettingsResponse get() {
        Settings settings = Settings.getOrCreate();
        return toResponse(settings);
    }

    /**
     * 更新系统设置。
     * 注意：如果 aiApiKey 为 "******" 或空字符串，则保留原值（不覆盖）。
     * @param request 更新请求
     * @return 更新后的设置响应
     * @throws IllegalArgumentException 校验失败时抛出
     */
    @Transactional
    public SettingsResponse update(UpdateSettingsRequest request) {
        // Validate
        if (request.taskIntervalHours < 0) {
            throw new IllegalArgumentException("任务间隔不能为负数");
        }
        if (request.aiApiUrl != null && !request.aiApiUrl.isBlank()) {
            if (!request.aiApiUrl.startsWith("http://") && !request.aiApiUrl.startsWith("https://")) {
                throw new IllegalArgumentException("AI API URL格式不正确");
            }
        }

        Settings settings = Settings.getOrCreate();

        settings.taskIntervalHours = request.taskIntervalHours;
        settings.aiModel = request.aiModel;

        if (request.aiApiUrl != null && !request.aiApiUrl.isBlank()) {
            settings.aiApiUrl = request.aiApiUrl;
        }

        // Handle API key: if "******" or empty, keep old value
        if (request.aiApiKey != null && !request.aiApiKey.isBlank()
            && !"******".equals(request.aiApiKey)) {
            settings.aiApiKey = request.aiApiKey;
        }

        if (request.defaultGroupId != null && request.defaultGroupId > 0) {
            settings.defaultGroup = org.rsscount.entity.RssGroup.findById(request.defaultGroupId);
        } else if (request.defaultGroupId != null && request.defaultGroupId == 0) {
            settings.defaultGroup = null;
        }

        settings.updatedAt = LocalDateTime.now();
        settings.persist();

        return toResponse(settings);
    }

    /** 将 Settings 实体转换为响应 DTO，API Key 自动脱敏 */
    private SettingsResponse toResponse(Settings settings) {
        return new SettingsResponse(
            settings.taskIntervalHours,
            settings.aiApiUrl,
            maskApiKey(settings.aiApiKey),
            settings.aiModel,
            settings.defaultGroup != null ? settings.defaultGroup.id : null,
            settings.updatedAt
        );
    }

    /**
     * Mask API key: show first 3 + **** + last 3.
     * Example: "sk-abc123xyz" → "sk-****...****xyz"
     */
    public static String maskApiKey(String key) {
        if (key == null || key.length() <= 6) {
            return key;
        }
        return key.substring(0, 3) + "****...****" + key.substring(key.length() - 3);
    }
}
