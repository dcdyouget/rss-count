package org.rsscount.util;

import java.io.OutputStream;
import java.util.List;

/**
 * OPML 写入器接口 — 将 RSS 源和分组导出为 OPML XML 格式。
 * 生成 OPML 2.0 兼容的 XML 文档。
 */
public interface OpmlWriter {

    /**
     * 将分组和源列表写入 OPML XML 输出流。
     * @param groups 分组条目列表（每个分组包含其下的源列表）
     * @param outputStream 目标输出流
     */
    void write(List<GroupEntry> groups, OutputStream outputStream) throws Exception;

    /**
     * RSS 分组条目（含该分组下的所有 RSS 源）。
     * @param groupName 分组名称
     * @param sources 该分组下的 RSS 源列表
     */
    record GroupEntry(String groupName, List<SourceEntry> sources) {}

    /**
     * RSS 源条目。
     * @param name 源显示名称
     * @param xmlUrl 源的 RSS XML URL
     */
    record SourceEntry(String name, String xmlUrl) {}
}
