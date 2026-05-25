package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.service.DraftService;

import java.util.List;
import java.util.Map;

/**
 * 稿件管理 REST 接口。
 * 路由前缀: /api/v1/drafts
 * 负责稿件的增删改查以及 AI 生成稿件功能。
 */
@Path("/api/v1/drafts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DraftController {

    @Inject
    DraftService draftService;

    // ── 18. GET /drafts — List with pagination ─────────────

    /**
     * 分页查询稿件列表。
     * @param page 页码（1-based）
     * @param size 每页条数，默认20
     * @return 分页的稿件列表
     */
    @GET
    public DraftService.PaginatedResponse<DraftService.DraftListSummary> list(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("20") int size
    ) {
        return draftService.list(page, size);
    }

    // ── 19. GET /drafts/{id} — Detail ──────────────────────

    /**
     * 获取指定稿件的详细信息。
     * @param id 稿件ID
     * @return 稿件详情
     * @throws NotFoundException 稿件不存在时抛出
     */
    @GET
    @Path("/{id}")
    public DraftService.DraftResponse getDetail(@PathParam("id") Long id) {
        return draftService.getById(id);
    }

    // ── 17. POST /drafts — Create ─────────────────────────

    /**
     * 创建新稿件。
     * @param request 创建稿件的请求体
     * @return 201 Created，包含创建的稿件信息
     */
    @POST
    public Response create(DraftService.CreateDraftRequest request) {
        try {
            var result = draftService.create(request);
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", msg, "message", msg)).build();
        }
    }

    // ── 20. PUT /drafts/{id} — Update ──────────────────────

    /**
     * 更新指定稿件的内容。
     * @param id 稿件ID
     * @param request 更新请求体
     * @return 更新后的稿件信息
     * @throws NotFoundException 稿件不存在时抛出
     */
    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, DraftService.UpdateDraftRequest request) {
        try {
            var result = draftService.update(id, request);
            return Response.ok(result).build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", msg, "message", msg)).build();
        }
    }

    // ── 21. DELETE /drafts/{id} — Delete ───────────────────

    /**
     * 删除指定稿件。
     * @param id 稿件ID
     * @return 204 No Content
     * @throws NotFoundException 稿件不存在时抛出
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        try {
            draftService.delete(id);
            return Response.noContent().build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw e;
        }
    }

    // ── 22. GET /drafts/{id}/versions — Version history ──

    /**
     * 获取稿件的所有历史版本（按版本号降序）。
     * @param id 稿件ID
     * @return 版本信息列表
     * @throws NotFoundException 稿件不存在时抛出
     */
    @GET
    @Path("/{id}/versions")
    public List<DraftService.DraftVersionInfo> getVersions(@PathParam("id") Long id) {
        return draftService.getVersions(id);
    }

    // ── 23. POST /drafts/{id}/generate — AI generation ────

    /**
     * 使用 AI 生成指定稿件的内容。基于稿件关联的新闻源自动生成文本。
     * @param id 稿件ID
     * @return 生成的稿件内容
     * @throws NotFoundException 稿件不存在时抛出
     */
    @POST
    @Path("/{id}/generate")
    public Response generate(@PathParam("id") Long id) {
        try {
            var result = draftService.generate(id);
            return Response.ok(result).build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw e;
        } catch (jakarta.ws.rs.WebApplicationException e) {
            return Response.status(e.getResponse().getStatus())
                .entity(Map.of("message", e.getMessage())).build();
        }
    }
}
