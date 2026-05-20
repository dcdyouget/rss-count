package org.rsscount.service;

import io.quarkus.logging.Log;
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

@ApplicationScoped
public class RssSourceService {

    public static final String ICONS_DIR = "src/main/resources/static/icons/";

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

    public record CreateRssSourceRequest(
        String url,
        String name,
        List<Long> groupIds
    ) {}

    public record UpdateRssSourceRequest(
        String url,
        String name,
        List<Long> groupIds
    ) {}

    // ---- CRUD ----

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

    public void exportOpml(OutputStream outputStream) {
        try {
            List<RssGroup> groups = RssGroup.listAll();
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

            generateOpmlXml(entries, outputStream);
        } catch (Exception e) {
            Log.errorf("OPML export failed: %s", e.getMessage());
            throw new RuntimeException("OPML导出失败", e);
        }
    }

    // ---- Favicon ----

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

    private void parseOutlines(org.w3c.dom.Node parent, List<OpmlParser.Outline> result) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                && "outline".equals(child.getNodeName())) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) child;
                String text = el.getAttribute("text");
                String xmlUrl = el.getAttribute("xmlUrl");

                if (xmlUrl != null && !xmlUrl.isBlank()) {
                    // Source outline
                    result.add(new OpmlParser.Outline(
                        text != null ? text : xmlUrl, xmlUrl, null));
                } else {
                    // Group outline (may have children)
                    List<OpmlParser.Outline> childOutlines = new ArrayList<>();
                    parseOutlines(child, childOutlines);
                    result.add(new OpmlParser.Outline(
                        text != null ? text : "未命名分组", null, childOutlines));
                }
            }
        }
    }

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

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;");
    }
}
