package org.rsscount.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * NewsTag 联合主键类 — 包含 newsId 和 tagId。
 * 实现 Serializable 和 equals/hashCode 用于 JPA 复合主键映射。
 */
public class NewsTagId implements Serializable {

    /** 新闻 ID */
    public Long newsId;

    /** 标签 ID */
    public Long tagId;

    public NewsTagId() {
    }

    public NewsTagId(Long newsId, Long tagId) {
        this.newsId = newsId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewsTagId newsTagId = (NewsTagId) o;
        return Objects.equals(newsId, newsTagId.newsId) &&
               Objects.equals(tagId, newsTagId.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(newsId, tagId);
    }
}
