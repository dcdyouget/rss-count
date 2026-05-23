package org.rsscount.service;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.rsscount.entity.RssGroup;
import org.rsscount.entity.RssSource;
import org.rsscount.entity.RssSourceGroup;
import org.rsscount.util.OpmlParser;
import org.rsscount.util.OpmlWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RSS 源管理服务 — 提供 RSS 源的 CRUD、搜索、导入/导出和 Favicon 下载功能。
 * 支持按分组查询、OPML 格式导入导出。
 */
@ApplicationScoped
public class RssSourceService {

    /** Favicon 图标文件的本地存储目录 */
    public static final String ICONS_DIR = "src/main/resources/static/icons/";

    /** 分页响应 */
    public record PagedResponse<T>(long total, int page, int size, List<T> items) {}

    /** RSS 源响应 — 包含关联分组信息 */
    public record RssSourceResponse(
        Long id,
        String url,
        String name,
        String iconPath,
        LocalDateTime createdAt,
        LocalDateTime lastFetchAt,
        int totalFetched,
        boolean isActive,
        List<Long> groupIds,
        List<String> groupNames
    ) {}

    /** 创建 RSS 源请求 */
    public record CreateRssSourceRequest(
        String url,
        String name,
        List<Long> groupIds
    ) {}

    /** 更新 RSS 源请求 */
    public record UpdateRssSourceRequest(
        String url,
        String name,
        List<Long> groupIds
    ) {}

    // ---- Search ----

    /**
     * 按关键字搜索 RSS 源（模糊匹配名称）。
     * @param keyword 搜索关键字
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @return 分页的 RSS 源列表
     */
    public PagedResponse<RssSourceResponse> search(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new PagedResponse<>(0, page, size, List.of());
        }
        long total = RssSource.count("name like ?1 and isActive = true", "%" + keyword + "%");
        List<RssSource> sources = RssSource.find("name like ?1 and isActive = true",
            Sort.by("createdAt").descending(), "%" + keyword + "%")
            .page(Page.of(page - 1, size)).list();
        List<RssSourceResponse> items = sources.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return new PagedResponse<>(total, page, size, items);
    }

    // ---- CRUD ----

    /**
     * 获取 RSS 源列表，可按分组过滤。
     * @param groupId 分组 ID（null 表示所有源）
     * @return RSS 源响应列表
     */
    public List<RssSourceResponse> list(Long groupId) {
        List<RssSource> sources;
        if (groupId != null) {
            List<Long> sourceIds = RssSourceGroup.find("rssGroupId", groupId)
                .stream()
                .map(sg -> ((RssSourceGroup) sg).rssSourceId)
                .toList();
            if (sourceIds.isEmpty()) {
                return List.of();
            }
            sources = RssSource.list("id in ?1 and isActive = true", sourceIds);
        } else {
            sources = RssSource.list("isActive = true");
        }
        return sources.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 创建 RSS 源。
     * 会自动去重、尝试从 RSS XML 解析标题、下载 favicon。
     * @param request 创建请求
     * @return 创建的 RSS 源响应
     * @throws IllegalArgumentException URL 为空时抛出
     * @throws IllegalStateException 源已存在时抛出
     */
    @Transactional
    public RssSourceResponse create(CreateRssSourceRequest request) {
        if (request.url == null || request.url.isBlank()) {
            throw new IllegalArgumentException("URL不能为空");
        }

        // Check duplicate
        RssSource existing = RssSource.find("url", request.url).firstResult();
        if (existing != null) {
            throw new IllegalStateException("RSS源已存在");  // 409
        }

        RssSource source = new RssSource();
        source.url = request.url;

        // Try to fetch name from RSS XML
        String name = (request.name != null && !request.name.isBlank())
            ? request.name
            : fetchTitleFromRss(request.url);
        source.name = (name != null && !name.isBlank()) ? name : request.url;
        source.createdAt = LocalDateTime.now();
        source.isActive = true;
        source.persist();
        Panache.getEntityManager().flush();  // flush before query to avoid JTA isolation

        // Associate groups
        updateSourceGroups(source.id, request.groupIds);

        // Try to download favicon
        try {
            downloadFavicon(source);
        } catch (Exception e) {
            Log.warnf("Failed to download favicon for source %d: %s", source.id, e.getMessage());
        }

        return toResponse(source);
    }

    /**
     * 更新 RSS 源信息。
     * @param id 源 ID
     * @param request 更新请求
     * @return 更新后的 RSS 源响应
     * @throws NotFoundException 源不存在时抛出
     */
    @Transactional
    public RssSourceResponse update(Long id, UpdateRssSourceRequest request) {
        RssSource source = RssSource.findById(id);
        if (source == null) {
            throw new NotFoundException("RSS源不存在");
        }

        if (request.url != null && !request.url.isBlank()) {
            // Check duplicate URL on different source
            RssSource dup = RssSource.find("url = ?1 and id != ?2", request.url, id).firstResult();
            if (dup != null) {
                throw new IllegalStateException("RSS源URL已存在");
            }
            source.url = request.url;
        }

        if (request.name != null && !request.name.isBlank()) {
            source.name = request.name;
        }

        if (request.groupIds != null) {
            updateSourceGroups(id, request.groupIds);
        }

        source.persist();
        return toResponse(source);
    }

    /**
     * 软删除 RSS 源（设置 isActive = false）。
     * @param id 源 ID
     * @throws NotFoundException 源不存在时抛出
     */
    @Transactional
    public void delete(Long id) {
        RssSource source = RssSource.findById(id);
        if (source == null) {
            throw new NotFoundException("RSS源不存在");
        }
        source.isActive = false;
        source.persist();
    }

    // ---- OPML Import/Export ----

    /**
     * 导入 OPML 文件中的 RSS 源。
     * 自动创建分组和源，已存在的源跳过。
     * @param inputStream OPML 文件的输入流
     * @return 导入结果（created：新建数，skipped：跳过数，errors：错误列表，total：总数）
     */
    @Transactional
    public Map<String, Object> importOpml(InputStream inputStream) {
        Map<String, Object> result = new LinkedHashMap<>();
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        int total = 0;

        try {
            // Use a simple XML-based parser since OpmlParser interface exists but may not be implemented
            List<OpmlParser.Outline> outlines = parseOpmlSimple(inputStream);

            for (OpmlParser.Outline outline : outlines) {
                if (outline.isGroup()) {
                    String groupName = outline.name() != null ? outline.name() : "默认";
                    RssGroup group = findOrCreateGroup(groupName);

                    if (outline.children() != null) {
                        for (OpmlParser.Outline child : outline.children()) {
                            total++;
                            if (child.isSource() && child.xmlUrl() != null) {
                                try {
                                    RssSource existing = RssSource.find("url", child.xmlUrl()).firstResult();
                                    if (existing != null) {
                                        // Ensure group association
                                        ensureGroupAssociation(existing.id, group.id);
                                        skipped++;
                                    } else {
                                        RssSource source = new RssSource();
                                        source.url = child.xmlUrl();
                                        source.name = child.name() != null ? child.name() : child.xmlUrl();
                                        source.createdAt = LocalDateTime.now();
                                        source.persist();

                                        RssSourceGroup sg = new RssSourceGroup();
                                        sg.rssSourceId = source.id;
                                        sg.rssGroupId = group.id;
                                        sg.persist();
                                        created++;
                                    }
                                } catch (Exception e) {
                                    errors.add("导入失败: " + child.xmlUrl() + " - " + e.getMessage());
                                }
                            }
                        }
                    }
                } else if (outline.isSource() && outline.xmlUrl() != null) {
                    total++;
                    try {
                        RssSource existing = RssSource.find("url", outline.xmlUrl()).firstResult();
                        if (existing != null) {
                            skipped++;
                        } else {
                            RssSource source = new RssSource();
                            source.url = outline.xmlUrl();
                            source.name = outline.name() != null ? outline.name() : outline.xmlUrl();
                            source.createdAt = LocalDateTime.now();
                            source.persist();
                            created++;
                        }
                    } catch (Exception e) {
                        errors.add("导入失败: " + outline.xmlUrl() + " - " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            errors.add("OPML解析失败: " + e.getMessage());
        }

        result.put("created", created);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("total", total);
        return result;
    }

    /**
     * 导出 RSS 源为 OPML 格式。
     * @param outputStream 输出流
     * @param groupIds 要导出的分组 ID 列表（null 或空表示导出所有）
     */
    public void exportOpml(OutputStream outputStream, List<Long> groupIds) {
        try {
            List<RssGroup> groups;
            if (groupIds != null && !groupIds.isEmpty()) {
                groups = RssGroup.list("id in ?1", groupIds);
            } else {
                groups = RssGroup.listAll();
            }
            List<OpmlWriter.GroupEntry> entries = new ArrayList<>();

            // Sources without groups go to "未分组"
            Map<Long, List<RssSource>> groupSources = new LinkedHashMap<>();

            for (RssGroup group : groups) {
                List<Long> sourceIds = RssSourceGroup.find("rssGroupId", group.id)
                    .stream()
                    .map(sg -> ((RssSourceGroup) sg).rssSourceId)
                    .toList();
                List<RssSource> sources = RssSource.list("id in ?1 and isActive = true", sourceIds);
                groupSources.put(group.id, sources);
            }

            for (RssGroup group : groups) {
                List<RssSource> sources = groupSources.getOrDefault(group.id, List.of());
                if (!sources.isEmpty() || groups.size() == 1) {
                    List<OpmlWriter.SourceEntry> sourceEntries = sources.stream()
                        .map(s -> new OpmlWriter.SourceEntry(s.name, s.url))
                        .collect(Collectors.toList());
                    entries.add(new OpmlWriter.GroupEntry(group.name, sourceEntries));
                }
            }

            // Only include ungrouped sources when exporting all groups (no filter)
            if (groupIds == null || groupIds.isEmpty()) {
                List<Long> groupedSourceIds = RssSourceGroup.findAll()
                    .stream()
                    .map(sg -> ((RssSourceGroup) sg).rssSourceId)
                    .distinct()
                    .toList();
                List<RssSource> ungrouped;
                if (groupedSourceIds.isEmpty()) {
                    ungrouped = RssSource.list("isActive", true);
                } else {
                    ungrouped = RssSource.list("isActive = true and id not in ?1", groupedSourceIds);
                }
                if (!ungrouped.isEmpty()) {
                    List<OpmlWriter.SourceEntry> srcEntries = ungrouped.stream()
                        .map(s -> new OpmlWriter.SourceEntry(s.name, s.url))
                        .collect(Collectors.toList());
                    entries.add(new OpmlWriter.GroupEntry("未分组", srcEntries));
                }
            }

            generateOpmlXml(entries, outputStream);
        } catch (Exception e) {
            Log.errorf("OPML export failed: %s", e.getMessage());
            throw new RuntimeException("OPML导出失败", e);
        }
    }

    // ---- Favicon ----

    /**
     * 下载 RSS 源的 favicon 图标并保存到本地。
     * 图标文件命名为 {source.id}.ico，存储在 ICONS_DIR 目录。
     * 下载失败仅记录日志，不阻断流程。
     * @param source RSS 源实体
     */
    public void downloadFavicon(RssSource source) {
        try {
            URL url = new URL(source.url);
            String faviconUrl = url.getProtocol() + "://" + url.getHost() + "/favicon.ico";

            Path iconsDir = Paths.get(ICONS_DIR);
            Files.createDirectories(iconsDir);

            String ext = "ico";
            String fileName = source.id + "." + ext;
            Path targetPath = iconsDir.resolve(fileName);

            try (InputStream in = new URL(faviconUrl).openStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            source.iconPath = "/static/icons/" + fileName;
            source.persist();
            Log.infof("Favicon downloaded for source %d: %s", source.id, faviconUrl);
        } catch (Exception e) {
            Log.warnf("Failed to download favicon for source %d: %s", source.id, e.getMessage());
        }
    }

    // ---- Private helpers ----

    /** 将 RssSource 实体转换为响应 DTO，包含关联分组信息 */
    private RssSourceResponse toResponse(RssSource source) {
        List<RssSourceGroup> associations = RssSourceGroup.find("rssSourceId", source.id).list();
        List<Long> groupIds = associations.stream()
            .map(sg -> ((RssSourceGroup) sg).rssGroupId)
            .collect(Collectors.toList());

        List<String> groupNames = List.of();
        if (!groupIds.isEmpty()) {
            List<RssGroup> groups = RssGroup.list("id in ?1", groupIds);
            groupNames = groups.stream().map(g -> ((RssGroup) g).name).collect(Collectors.toList());
        }

        return new RssSourceResponse(
            source.id,
            source.url,
            source.name,
            source.iconPath,
            source.createdAt,
            source.lastFetchAt,
            source.totalFetched,
            source.isActive,
            groupIds,
            groupNames
        );
    }

    /** 更新 RSS 源的分组关联：先删除旧关联，再创建新关联 */
    private void updateSourceGroups(Long sourceId, List<Long> groupIds) {
        // Delete old associations (iterate to avoid JPQL bulk-delete flush issues with SQLite)
        List<RssSourceGroup> existing = RssSourceGroup.list("rssSourceId", sourceId);
        for (RssSourceGroup sg : existing) {
            sg.delete();
        }

        // Create new associations
        if (groupIds != null) {
            for (Long groupId : groupIds) {
                RssGroup group = RssGroup.findById(groupId);
                if (group != null) {
                    RssSourceGroup sg = new RssSourceGroup();
                    sg.rssSourceId = sourceId;
                    sg.rssGroupId = groupId;
                    sg.persist();
                }
            }
        }
    }

    /** 根据名称查找或创建 RSS 分组（导入时使用） */
    private RssGroup findOrCreateGroup(String name) {
        RssGroup group = RssGroup.find("name", name).firstResult();
        if (group == null) {
            group = new RssGroup();
            group.name = name;
            group.createdAt = LocalDateTime.now();
            group.persist();
        }
        return group;
    }

    /** 确保 RSS 源与分组的关联存在（导入已存在源时调用） */
    private void ensureGroupAssociation(Long sourceId, Long groupId) {
        RssSourceGroup existing = RssSourceGroup.find(
            "rssSourceId = ?1 and rssGroupId = ?2", sourceId, groupId).firstResult();
        if (existing == null) {
            RssSourceGroup sg = new RssSourceGroup();
            sg.rssSourceId = sourceId;
            sg.rssGroupId = groupId;
            sg.persist();
        }
    }

    /** 从 RSS XML 中解析频道标题（用于创建源时自动命名） */
    private String fetchTitleFromRss(String url) {
        try {
            // Simple RSS XML parsing to get the channel title
            java.net.URL rssUrl = new java.net.URL(url);
            javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(rssUrl.openStream());
            org.w3c.dom.NodeList titles = doc.getElementsByTagName("title");
            if (titles.getLength() > 0) {
                org.w3c.dom.Node channelTitle = titles.item(0);
                return channelTitle.getTextContent().trim();
            }
        } catch (Exception e) {
            Log.debugf("Failed to fetch RSS title from %s: %s", url, e.getMessage());
        }
        return null;
    }

    /** 简易 OPML XML 解析器，将 XML 文档解析为 Outline 列表 */
    private List<OpmlParser.Outline> parseOpmlSimple(InputStream inputStream) throws Exception {
        List<OpmlParser.Outline> outlines = new ArrayList<>();

        javax.xml.parsers.DocumentBuilderFactory factory =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(inputStream);

        org.w3c.dom.NodeList bodyNodes = doc.getElementsByTagName("body");
        if (bodyNodes.getLength() > 0) {
            parseOutlines(bodyNodes.item(0), outlines);
        }
        return outlines;
    }

    /** 递归解析 outline 节点，区分分组（含子节点）和源（有 xmlUrl） */
    private void parseOutlines(org.w3c.dom.Node parent, List<OpmlParser.Outline> result) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                && "outline".equals(child.getNodeName())) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) child;
                String text = el.getAttribute("text");
                String title = el.getAttribute("title");
                String xmlUrl = el.getAttribute("xmlUrl");

                // Prefer title over text; only use text when it is non-blank and differs from title
                String name = (text != null && !text.isBlank() && !text.equals(title)) ? text : title;

                if (xmlUrl != null && !xmlUrl.isBlank()) {
                    // Source outline
                    result.add(new OpmlParser.Outline(
                        name != null ? name : xmlUrl, xmlUrl, null));
                } else {
                    // Group outline (may have children)
                    List<OpmlParser.Outline> childOutlines = new ArrayList<>();
                    parseOutlines(child, childOutlines);
                    result.add(new OpmlParser.Outline(
                        name != null ? name : "未命名分组", null, childOutlines));
                }
            }
        }
    }

    /** 生成 OPML 2.0 格式的 XML 并写入输出流 */
    private void generateOpmlXml(List<OpmlWriter.GroupEntry> entries, OutputStream out) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<opml version=\"2.0\">\n");
        xml.append("  <head>\n");
        xml.append("    <title>RSS Count - RSS源导出</title>\n");
        xml.append("    <dateCreated>").append(java.time.LocalDateTime.now()).append("</dateCreated>\n");
        xml.append("  </head>\n");
        xml.append("  <body>\n");

        for (OpmlWriter.GroupEntry group : entries) {
            xml.append("    <outline text=\"").append(escapeXml(group.groupName())).append("\">\n");
            for (OpmlWriter.SourceEntry source : group.sources()) {
                xml.append("      <outline text=\"").append(escapeXml(source.name()))
                    .append("\" xmlUrl=\"").append(escapeXml(source.xmlUrl())).append("\" type=\"rss\"/>\n");
            }
            xml.append("    </outline>\n");
        }

        xml.append("  </body>\n");
        xml.append("</opml>\n");

        out.write(xml.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }

    /** XML 转义：替换 &、"、<、>、' 为对应的实体引用 */
    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;");
    }
}
