package org.rsscount.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * DraftNews 联合主键类 — 包含 draftId 和 newsId。
 * 实现 Serializable 和 equals/hashCode 用于 JPA 复合主键映射。
 */
public class DraftNewsId implements Serializable {

    /** 稿件 ID */
    public Long draftId;

    /** 新闻 ID */
    public Long newsId;

    public DraftNewsId() {
    }

    public DraftNewsId(Long draftId, Long newsId) {
        this.draftId = draftId;
        this.newsId = newsId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DraftNewsId that = (DraftNewsId) o;
        return Objects.equals(draftId, that.draftId) &&
               Objects.equals(newsId, that.newsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(draftId, newsId);
    }
}
