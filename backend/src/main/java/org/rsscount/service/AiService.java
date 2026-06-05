package org.rsscount.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.rsscount.entity.Settings;
import org.rsscount.entity.Tag;

import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;

/**
 * AI service for generating summaries and extracting tags.
 * Uses OpenAI-compatible API with config from Settings entity.
 */
@ApplicationScoped
public class AiService {

    /**
     * AI provider interface for dependency injection and testing.
     */
    public interface AiProvider {
        /** Generate a summary of the content, limited to maxLength characters. */
        String generateSummary(String content, int maxLength);

        /** Extract tags from content and match against the tag library. */
        List<String> extractTags(String content);

        /** Generate a full draft article from the given prompt. */
        String generateDraft(String prompt, double temperature);
    }

    /**
     * OpenAI-compatible REST client interface.
     * Base URI is set programmatically from Settings.
     */
    @Path("/chat/completions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public interface OpenAiRestClient extends AutoCloseable {
        @POST
        ChatResponse chat(ChatRequest request);

        @Override
        void close();

        static OpenAiRestClient build(URI baseUri, String apiKey) {
            return build(baseUri, apiKey, 30);
        }

        static OpenAiRestClient build(URI baseUri, String apiKey, long timeoutSeconds) {
            return RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .register((ClientRequestFilter) ctx ->
                    ctx.getHeaders().add("Authorization", "Bearer " + apiKey))
                .build(OpenAiRestClient.class);
        }
    }

    // ---- Request/Response DTOs ----

    public static class ChatRequest {
        public String model;
        public List<Message> messages;
        @JsonProperty("max_tokens")
        public int maxTokens;
        public double temperature = 0.3;

        public ChatRequest() {}

        public ChatRequest(String model, List<Message> messages, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.maxTokens = maxTokens;
        }
    }

    public static class Message {
        public String role;
        public String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class ChatResponse {
        public List<Choice> choices;

        public ChatResponse() {}

        public String getContent() {
            if (choices != null && !choices.isEmpty() && choices.get(0).message != null) {
                return choices.get(0).message.content;
            }
            return "";
        }
    }

    public static class Choice {
        public Message message;
        public int index;
    }

    /**
     * Default AI provider implementation using OpenAI-compatible API.
     */
    @ApplicationScoped
    public static class OpenAiCompatibleProvider implements AiProvider {

        private OpenAiRestClient buildClient(Settings settings) {
            URI baseUri = URI.create(settings.aiApiUrl);
            return OpenAiRestClient.build(baseUri, settings.aiApiKey);
        }

        @Override
        public String generateSummary(String content, int maxLength) {
            try {
                Settings settings = Settings.getOrCreate();
                if (isSettingsIncomplete(settings)) {
                    Log.warn("AI settings not configured, returning empty summary");
                    return "";
                }

                Log.infof("AI: Requesting summary (model=%s, maxLen=%d, inputLen=%d)",
                    settings.aiModel, maxLength, content.length());

                String systemPrompt = "你是一个专业的内容摘要生成助手。请根据以下文章内容，生成"
                    + maxLength + "字以内的中文摘要。只返回摘要文本，不要包含任何前缀或解释。";
                String userContent = content.length() > 2000 ? content.substring(0, 2000) : content;

                ChatRequest request = new ChatRequest(
                    settings.aiModel,
                    List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userContent)
                    ),
                    800
                );

                try (OpenAiRestClient client = buildClient(settings)) {
                    ChatResponse response = client.chat(request);
                    String result = response.getContent();
                    Log.infof("AI: Summary response received (%d chars)", result != null ? result.length() : 0);
                    if (result != null && result.length() > maxLength) {
                        result = result.substring(0, maxLength);
                    }
                    return result != null ? result.trim() : "";
                }
            } catch (Exception e) {
                Log.warnf("AI generateSummary failed (degraded): %s", e.getMessage());
                return "";
            }
        }

        @Override
        public List<String> extractTags(String content) {
            try {
                Settings settings = Settings.getOrCreate();
                if (isSettingsIncomplete(settings)) {
                    Log.warn("AI settings not configured, returning empty tags");
                    return Collections.emptyList();
                }

                Log.infof("AI: Requesting tags (model=%s, inputLen=%d)",
                    settings.aiModel, content.length());

                String systemPrompt = """
                    你是一个专业的内容标签提取助手。请根据以下文章内容，提取2-5个关键标签。
                    只返回标签列表，每行一个标签，不要包含数字编号、破折号或其他前缀。
                    标签应该简洁（2-5个字），使用中文。
                    示例输出：
                    人工智能
                    融资
                    科技新闻""";

                String userContent = content.length() > 2000 ? content.substring(0, 2000) : content;

                ChatRequest request = new ChatRequest(
                    settings.aiModel,
                    List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userContent)
                    ),
                    200
                );

                try (OpenAiRestClient client = buildClient(settings)) {
                    ChatResponse response = client.chat(request);
                    String rawTags = response.getContent();

                    Log.infof("AI: Tags raw response (%d chars)", rawTags != null ? rawTags.length() : 0);

                    if (rawTags == null || rawTags.isBlank()) {
                        return Collections.emptyList();
                    }

                    // Parse tags: split by newline, clean up
                    List<String> extractedTags = new ArrayList<>();
                    for (String line : rawTags.split("\n")) {
                        String tag = line.trim()
                            .replaceAll("^[\\d\\.\\-、\\s]+", "")
                            .replaceAll("[\\[\\]【】]", "")
                            .trim();
                        if (!tag.isEmpty() && tag.length() <= 20) {
                            extractedTags.add(tag);
                        }
                    }

                    // Match against tag library
                    List<Tag> existingTags = Tag.listAll();
                    List<String> matchedTags = new ArrayList<>();
                    for (String extracted : extractedTags) {
                        for (Tag tag : existingTags) {
                            if (tag.name.equals(extracted)
                                || tag.name.contains(extracted)
                                || extracted.contains(tag.name)) {
                                if (!matchedTags.contains(tag.name)) {
                                    matchedTags.add(tag.name);
                                }
                                break;
                            }
                        }
                    }

                    Log.infof("AI: Tags matched: %s", String.join(", ", matchedTags));
                    return matchedTags;
                }
            } catch (Exception e) {
                Log.warnf("AI extractTags failed (degraded): %s", e.getMessage());
                return Collections.emptyList();
            }
        }

        @Override
        public String generateDraft(String prompt, double temperature) {
            try {
                Settings settings = Settings.getOrCreate();
                if (isSettingsIncomplete(settings)) {
                    Log.warn("AI settings not configured, cannot generate draft");
                    throw new IllegalStateException("AI 设置未配置");
                }

                ChatRequest request = new ChatRequest(
                    settings.aiModel,
                    List.of(new Message("user", prompt)),
                    4096
                );
                request.temperature = temperature;

                try (OpenAiRestClient client = OpenAiRestClient.build(
                        URI.create(settings.aiApiUrl), settings.aiApiKey, 120)) {
                    ChatResponse response = client.chat(request);
                    String content = response.getContent();
                    return content != null ? content.trim() : "";
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                Log.warnf("AI generateDraft failed: %s", e.getMessage());
                throw new RuntimeException("AI 服务不可用: " + e.getMessage(), e);
            }
        }

        private boolean isSettingsIncomplete(Settings settings) {
            return settings.aiApiUrl == null || settings.aiApiUrl.isBlank()
                || settings.aiApiKey == null || settings.aiApiKey.isBlank()
                || settings.aiModel == null || settings.aiModel.isBlank();
        }
    }

    // ---- Convenience methods that delegate to the default provider ----

    private final OpenAiCompatibleProvider defaultProvider;

    public AiService(OpenAiCompatibleProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public String generateSummary(String content, int maxLength) {
        return defaultProvider.generateSummary(content, maxLength);
    }

    public List<String> extractTags(String content) {
        return defaultProvider.extractTags(content);
    }

    public String generateDraft(String prompt, double temperature) {
        return defaultProvider.generateDraft(prompt, temperature);
    }
}
