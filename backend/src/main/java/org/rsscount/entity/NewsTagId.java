package org.rsscount.entity;

import java.io.Serializable;
import java.util.Objects;

public class NewsTagId implements Serializable {

    public Long newsId;
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
