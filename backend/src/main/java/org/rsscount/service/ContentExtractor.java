package org.rsscount.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

/**
 * HTML content extractor — cleans raw HTML into a safe, sanitized subset.
 * Strips scripts, styles, and noise elements; normalizes URLs; enforces
 * security attributes on links and images.
 */
@ApplicationScoped
public class ContentExtractor {

    private static final String[] NOISE_SELECTORS = {
        "script", "style", "iframe", "noscript", "form", "button",
        ".ad", ".sidebar", ".comment", ".comments", ".menu",
        ".navigation", ".breadcrumb", ".share", ".social",
        "#footer", "#header", "#nav", "#sidebar", "#comments"
    };

    /**
     * Clean and sanitize raw HTML content.
     * <p>
     * Removes noise elements (scripts, styles, ads, navigation, etc.),
     * then passes the remainder through a safe HTML whitelist that strips
     * dangerous attributes and normalizes relative URLs to absolute.
     *
     * @param html    raw HTML input
     * @param baseUri base URI for resolving relative URLs (e.g. article source URL)
     * @return cleaned HTML string; null if input is blank
     */
    public String clean(String html, String baseUri) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Document doc = Jsoup.parse(html);

        // Remove noise elements
        for (String selector : NOISE_SELECTORS) {
            doc.select(selector).remove();
        }

        // Configure safe HTML whitelist
        Safelist safelist = Safelist.relaxed()
            .addTags("figure", "figcaption", "hr", "article", "section", "header", "footer")
            .addEnforcedAttribute("a", "target", "_blank")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")
            .preserveRelativeLinks(false);

        String bodyHtml = doc.body() != null ? doc.body().html() : "";
        return Jsoup.clean(bodyHtml, baseUri != null ? baseUri : "", safelist);
    }

    /**
     * Extract the first meaningful image HTML tag from HTML content.
     *
     * @param html raw HTML
     * @return first image outer HTML (&lt;img ...&gt;), or null
     */
    public String extractHeaderImage(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(html);
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
                    return img.outerHtml();
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }
}
