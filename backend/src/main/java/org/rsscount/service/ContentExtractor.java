package org.rsscount.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML content extractor — parses HTML into structured JSON.
 * Outputs a JSON array of block objects: heading, paragraph, image, blockquote, list, code.
 */
@ApplicationScoped
public class ContentExtractor {

    private static final String[] NOISE_SELECTORS = {
        "script", "style", "nav", "footer", "header", "aside",
        ".ad", ".sidebar", ".comment", ".comments", ".menu",
        ".navigation", ".breadcrumb", ".share", ".social",
        "#footer", "#header", "#nav", "#sidebar", "#comments",
        "iframe", "noscript", "form", "button"
    };

    /**
     * Extract structured content blocks from HTML.
     *
     * @param html raw HTML
     * @return JSON array string of blocks; null if html is blank or parsing fails
     */
    public String extract(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(html);

            // Remove noise elements
            for (String selector : NOISE_SELECTORS) {
                doc.select(selector).remove();
            }

            Element body = doc.body();
            if (body == null) {
                body = doc;
            }

            List<Block> blocks = new ArrayList<>();

            // Process child nodes in order
            processChildren(body, blocks);

            return toJsonArray(blocks);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract the first image URL from HTML content.
     *
     * @param html raw HTML
     * @return first image src, or null
     */
    public String extractHeaderImage(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(html);
            // Try to find the first meaningful image
            Elements imgs = doc.select("article img, .content img, .post img, .entry img, main img, img");
            for (Element img : imgs) {
                String src = img.attr("src");
                if (src != null && !src.isBlank()) {
                    // Skip icons, placeholders, tracking pixels
                    if (src.contains("icon") || src.contains("avatar")
                        || src.contains("logo") || src.contains("placeholder")
                        || src.contains("pixel") || src.contains("spacer")
                        || src.endsWith(".svg")) {
                        continue;
                    }
                    return src;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    // ── Internal types ──────────────────────────────────────

    record Block(String type, String text, Integer level, String src, String alt, List<String> items) {}

    // ── Private helpers ─────────────────────────────────────

    private void processChildren(Element parent, List<Block> blocks) {
        for (Element child : parent.children()) {
            String tag = child.tagName().toLowerCase();
            String text = child.wholeText().trim();

            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (!text.isEmpty()) {
                        int level = Integer.parseInt(tag.substring(1));
                        blocks.add(new Block("heading", text, level, null, null, null));
                    }
                }
                case "p" -> {
                    String pText = collectText(child).trim();
                    if (!pText.isEmpty()) {
                        blocks.add(new Block("paragraph", pText, null, null, null, null));
                    }
                }
                case "img" -> {
                    String src = child.attr("src");
                    if (src != null && !src.isBlank()) {
                        String alt = child.attr("alt");
                        blocks.add(new Block("image", null, null, src, alt != null ? alt : "", null));
                    }
                }
                case "blockquote" -> {
                    String bqText = collectText(child).trim();
                    if (!bqText.isEmpty()) {
                        blocks.add(new Block("blockquote", bqText, null, null, null, null));
                    }
                }
                case "ul", "ol" -> {
                    List<String> items = new ArrayList<>();
                    for (Element li : child.children()) {
                        String liText = collectText(li).trim();
                        if (!liText.isEmpty()) {
                            items.add(liText);
                        }
                    }
                    if (!items.isEmpty()) {
                        blocks.add(new Block("list", null, null, null, null, items));
                    }
                }
                case "pre", "code" -> {
                    String codeText = collectText(child).trim();
                    if (!codeText.isEmpty()) {
                        blocks.add(new Block("code", codeText, null, null, null, null));
                    }
                }
                case "div", "section", "article", "main", "figure" -> {
                    // Recurse into structural containers
                    processChildren(child, blocks);
                }
                default -> {
                    // For unrecognized block-level-ish tags, check if they contain text
                    if (isBlockLevel(tag) && !text.isEmpty()) {
                        blocks.add(new Block("paragraph", text, null, null, null, null));
                    }
                }
            }
        }
    }

    /**
     * Collect all text from an element (including inline elements).
     */
    private String collectText(Element element) {
        StringBuilder sb = new StringBuilder();
        for (Element child : element.children()) {
            String tag = child.tagName().toLowerCase();
            if ("br".equals(tag)) {
                sb.append('\n');
            } else {
                String t = child.wholeText().trim();
                if (!t.isEmpty()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t);
                }
            }
        }
        // Also include direct text nodes
        String ownText = element.ownText().trim();
        if (!ownText.isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(ownText);
        }
        return sb.toString();
    }

    private boolean isBlockLevel(String tag) {
        return switch (tag) {
            case "address", "article", "aside", "blockquote", "canvas", "dd", "div",
                 "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
                 "header", "hr", "li", "main", "nav", "noscript", "ol", "output",
                 "section", "table", "tfoot", "ul", "video" -> true;
            default -> false;
        };
    }

    /**
     * Serialize blocks to compact JSON array string.
     */
    private String toJsonArray(List<Block> blocks) {
        if (blocks.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append(",");
            Block b = blocks.get(i);
            sb.append("{");
            sb.append("\"type\":\"").append(escapeJson(b.type())).append("\"");

            if (b.text() != null) {
                sb.append(",\"text\":\"").append(escapeJson(b.text())).append("\"");
            }
            if (b.level() != null) {
                sb.append(",\"level\":").append(b.level());
            }
            if (b.src() != null) {
                sb.append(",\"src\":\"").append(escapeJson(b.src())).append("\"");
                if (b.alt() != null) {
                    sb.append(",\"alt\":\"").append(escapeJson(b.alt())).append("\"");
                }
            }
            if (b.items() != null && !b.items().isEmpty()) {
                sb.append(",\"items\":[");
                for (int j = 0; j < b.items().size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(b.items().get(j))).append("\"");
                }
                sb.append("]");
            }

            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
