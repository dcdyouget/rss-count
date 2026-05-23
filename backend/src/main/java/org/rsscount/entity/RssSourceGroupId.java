package org.rsscount.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * RssSourceGroup 联合主键类 — 包含 rssSourceId 和 rssGroupId。
 * 实现 Serializable 和 equals/hashCode 用于 JPA 复合主键映射。
 */
public class RssSourceGroupId implements Serializable {

    /** RSS 源 ID */
    public Long rssSourceId;

    /** RSS 分组 ID */
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
