package org.rsscount.util;

import java.io.InputStream;
import java.util.List;

/**
 * OPML 解析器接口 — 将 OPML XML 输入解析为 Outline 结构。
 * 实现类可使用 ROME 或自定义 XML 解析。
 */
public interface OpmlParser {

    /**
     * OPML Outline 元素。
     * 分组：有 name，无 xmlUrl，有 children。
     * 源：有 name，有 xmlUrl，无 children。
     *
     * @param name 分组名称或源标题
     * @param xmlUrl 源的 RSS XML URL（分组为 null）
     * @param children 子 Outline（源为 null）
     */
    record Outline(String name, String xmlUrl, List<Outline> children) {
        /** 当前 Outline 是否为分组节点 */
        public boolean isGroup() {
            return xmlUrl == null && children != null;
        }

        /** 当前 Outline 是否为 RSS 源节点 */
        public boolean isSource() {
            return xmlUrl != null;
        }
    }

    /**
     * 解析 OPML XML 输入流，返回顶层 Outline 列表。
     * @param inputStream OPML 文件输入流
     * @return Outline 列表
     */
    List<Outline> parse(InputStream inputStream) throws Exception;
}
