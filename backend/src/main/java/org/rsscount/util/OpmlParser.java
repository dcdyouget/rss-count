package org.rsscount.util;

import java.io.InputStream;
import java.util.List;

/**
 * Parse OPML XML input into outlines.
 * Implementations may use ROME or custom XML parsing.
 */
public interface OpmlParser {

    /**
     * Parsed outline element from OPML.
     * A group has name, no xmlUrl, and children outlines.
     * A source has name, xmlUrl, and no children.
     */
    record Outline(String name, String xmlUrl, List<Outline> children) {
        public boolean isGroup() {
            return xmlUrl == null && children != null;
        }

        public boolean isSource() {
            return xmlUrl != null;
        }
    }

    /**
     * Parse OPML XML input stream and return top-level outlines.
     */
    List<Outline> parse(InputStream inputStream) throws Exception;
}
