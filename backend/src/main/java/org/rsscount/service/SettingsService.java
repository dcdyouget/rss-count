package org.rsscount.service;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.rsscount.entity.Settings;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SettingsService {

    public record SettingsResponse(
        int taskIntervalHours,
        String aiApiUrl,
        String aiApiKey,        // masked
        String aiModel,
        Long defaultGroupId,
        LocalDateTime updatedAt
    ) {}

    public record UpdateSettingsRequest(
        int taskIntervalHours,
        String aiApiUrl,
        String aiApiKey,
        String aiModel,
        Long defaultGroupId
    ) {}

    @Transactional
    public SettingsResponse get() {
        Settings settings = Settings.getOrCreate();
        return toResponse(settings);
    }

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
