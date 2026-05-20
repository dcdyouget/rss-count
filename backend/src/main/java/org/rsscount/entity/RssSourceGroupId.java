package org.rsscount.entity;

import java.io.Serializable;
import java.util.Objects;

public class RssSourceGroupId implements Serializable {

    public Long rssSourceId;
    public Long rssGroupId;

    public RssSourceGroupId() {
    }

    public RssSourceGroupId(Long rssSourceId, Long rssGroupId) {
        this.rssSourceId = rssSourceId;
        this.rssGroupId = rssGroupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RssSourceGroupId that = (RssSourceGroupId) o;
        return Objects.equals(rssSourceId, that.rssSourceId) &&
               Objects.equals(rssGroupId, that.rssGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rssSourceId, rssGroupId);
    }
}
