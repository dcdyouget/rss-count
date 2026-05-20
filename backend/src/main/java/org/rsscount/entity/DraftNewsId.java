package org.rsscount.entity;

import java.io.Serializable;
import java.util.Objects;

public class DraftNewsId implements Serializable {

    public Long draftId;
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
