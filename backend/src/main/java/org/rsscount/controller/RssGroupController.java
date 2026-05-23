package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.transaction.Transactional;
import org.rsscount.entity.RssGroup;
import org.rsscount.entity.RssSourceGroup;
import org.rsscount.service.RssGroupService;
import org.rsscount.service.RssGroupService.CreateGroupRequest;
import org.rsscount.service.RssGroupService.UpdateGroupRequest;

import java.util.List;
import java.util.Map;

/**
 * RSS 分组管理 REST 接口。
 * 路由前缀: /api/v1/rss-groups
 * 负责 RSS 源的增删改查以及分组内源的管理。
 */
@Path("/api/v1/rss-groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RssGroupController {

    @Inject
    RssGroupService service;

    /**
     * 获取所有 RSS 分组列表。
     * @return 分组列表
     */
    @GET
    public List<RssGroupService.RssGroupResponse> list() {
        return service.list();
    }

    /**
     * 创建新的 RSS 分组。
     * @param request 创建分组请求，包含分组名称等信息
     * @return 201 Created，包含创建的分组信息
     */
    @POST
    public Response create(CreateGroupRequest request) {
        try {
            var result = service.create(request);
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * 更新指定分组的名称等信息。
     * @param id 分组ID
     * @param request 更新请求
     * @return 更新后的分组信息
     */
    @PUT
    @Path("/{id}")
    public RssGroupService.RssGroupResponse update(
        @PathParam("id") Long id,
        UpdateGroupRequest request
    ) {
        return service.update(id, request);
    }

    /**
     * 删除指定的 RSS 分组。
     * @param id 分组ID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /**
     * 设置指定分组下的 RSS 源列表（全量替换）。
     * @param id 分组ID
     * @param sourceIds RSS 源ID列表
     * @return 200 OK
     */
    @PUT
    @Path("/{id}/sources")
    @Transactional
    public Response addSources(@PathParam("id") Long id, List<Long> sourceIds) {
        RssGroup group = RssGroup.findById(id);
        if (group == null) return Response.status(404).build();

        RssSourceGroup.delete("rssGroupId", id);
        if (sourceIds != null) {
            for (Long sourceId : sourceIds) {
                RssSourceGroup sg = new RssSourceGroup();
                sg.rssSourceId = sourceId;
                sg.rssGroupId = id;
                sg.persist();
            }
        }
        return Response.ok().build();
    }
}
