package org.rsscount.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.rsscount.entity.RssGroup;
import org.rsscount.entity.RssSourceGroup;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RSS 分组管理服务 — 提供分组的 CRUD 和源数量统计功能。
 */
@ApplicationScoped
public class RssGroupService {

    /** 分组响应（含源数量） */
    public record RssGroupResponse(
        Long id,
        String name,
        long sourceCount,
        LocalDateTime createdAt
    ) {}

    /** 创建分组请求 */
    public record CreateGroupRequest(String name) {}

    /** 更新分组请求 */
    public record UpdateGroupRequest(String name) {}

    /**
     * 获取全部分组列表（含每个分组的源数量）。
     * @return 分组响应列表
     */
    public List<RssGroupResponse> list() {
        List<RssGroup> groups = RssGroup.listAll();
        return groups.stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * 创建 RSS 分组（名称不可重复）。
     * @param request 创建请求
     * @return 创建的分组响应
     * @throws IllegalArgumentException 名称为空时抛出
     * @throws IllegalStateException 名称已存在时抛出
     */
    @Transactional
    public RssGroupResponse create(CreateGroupRequest request) {
        if (request.name == null || request.name.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }

        RssGroup existing = RssGroup.find("name", request.name).firstResult();
        if (existing != null) {
            throw new IllegalStateException("分组名称已存在");
        }

        RssGroup group = new RssGroup();
        group.name = request.name;
        group.createdAt = LocalDateTime.now();
        group.persist();

        return toResponse(group);
    }

    /**
     * 更新分组名称。
     * @param id 分组 ID
     * @param request 更新请求
     * @return 更新后的分组响应
     * @throws NotFoundException 分组不存在时抛出
     * @throws IllegalStateException 新名称已存在时抛出
     */
    @Transactional
    public RssGroupResponse update(Long id, UpdateGroupRequest request) {
        RssGroup group = RssGroup.findById(id);
        if (group == null) {
            throw new NotFoundException("分组不存在");
        }

        if (request.name == null || request.name.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }

        // Check duplicate name
        RssGroup dup = RssGroup.find("name = ?1 and id != ?2", request.name, id).firstResult();
        if (dup != null) {
            throw new IllegalStateException("分组名称已存在");
        }

        group.name = request.name;
        group.persist();

        return toResponse(group);
    }

    /**
     * 删除分组（同时删除关联的源-分组关系）。
     * @param id 分组 ID
     * @throws NotFoundException 分组不存在时抛出
     */
    @Transactional
    public void delete(Long id) {
        RssGroup group = RssGroup.findById(id);
        if (group == null) {
            throw new NotFoundException("分组不存在");
        }

        // Delete group-source associations
        RssSourceGroup.delete("rssGroupId", id);
        // Delete the group itself
        group.delete();
    }

    /** 将 RssGroup 实体转换为响应 DTO，并查询关联的源数量 */
    private RssGroupResponse toResponse(RssGroup group) {
        long sourceCount = RssSourceGroup.count("rssGroupId", group.id);
        return new RssGroupResponse(group.id, group.name, sourceCount, group.createdAt);
    }
}
