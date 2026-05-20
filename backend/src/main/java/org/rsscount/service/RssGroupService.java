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

@ApplicationScoped
public class RssGroupService {

    public record RssGroupResponse(
        Long id,
        String name,
        long sourceCount,
        LocalDateTime createdAt
    ) {}

    public record CreateGroupRequest(String name) {}

    public record UpdateGroupRequest(String name) {}

    public List<RssGroupResponse> list() {
        List<RssGroup> groups = RssGroup.listAll();
        return groups.stream()
            .map(this::toResponse)
            .toList();
    }

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

    private RssGroupResponse toResponse(RssGroup group) {
        long sourceCount = RssSourceGroup.count("rssGroupId", group.id);
        return new RssGroupResponse(group.id, group.name, sourceCount, group.createdAt);
    }
}
