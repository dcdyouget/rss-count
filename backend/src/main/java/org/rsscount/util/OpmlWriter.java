package org.rsscount.util;

import java.io.OutputStream;
import java.util.List;

/**
 * Write RSS sources and groups as OPML XML.
 */
public interface OpmlWriter {

    /**
     * Write OPML XML to the output stream.
     *
     * @param groups list of group entries with their sources
     * @param outputStream the stream to write OPML XML to
     */
    void write(List<GroupEntry> groups, OutputStream outputStream) throws Exception;

    /**
     * Represents an RSS group with its contained sources.
     */
    record GroupEntry(String groupName, List<SourceEntry> sources) {}

    /**
     * Represents an RSS source entry within a group.
     */
    record SourceEntry(String name, String xmlUrl) {}
}
