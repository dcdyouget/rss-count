package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.rsscount.service.RssSourceService;
import org.rsscount.service.RssSourceService.CreateRssSourceRequest;
import org.rsscount.service.RssSourceService.UpdateRssSourceRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * RSS 源管理 REST 接口。
 * 路由前缀: /api/v1/rss-sources
 * 负责 RSS 源的搜索、增删改查以及 OPML 导入导出。
 */
@Path("/api/v1/rss-sources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RssSourceController {

    @Inject
    RssSourceService service;

    /**
     * 分页搜索 RSS 源，支持关键词模糊匹配。
     * @param keyword 搜索关键词（可选）
     * @param page 页码（1-based）
     * @param size 每页条数，默认20
     * @return 分页的 RSS 源列表
     */
    @GET
    @Path("/search")
    public RssSourceService.PagedResponse<RssSourceService.RssSourceResponse> search(
        @QueryParam("keyword") String keyword,
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("20") int size) {
        return service.search(keyword, page, size);
    }

    /**
     * 获取 RSS 源列表，可按分组筛选。
     * @param groupId 分组ID（可选），指定时返回该分组下的源
     * @return RSS 源列表
     */
    @GET
    public List<RssSourceService.RssSourceResponse> list(
        @QueryParam("groupId") Long groupId
    ) {
        return service.list(groupId);
    }

    /**
     * 创建新的 RSS 源。
     * @param request 创建请求，包含 RSS URL 等信息
     * @return 201 Created，包含创建的 RSS 源信息；URL 冲突时返回 409
     */
    @POST
    public Response create(CreateRssSourceRequest request) {
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
     * 更新指定 RSS 源的信息。
     * @param id RSS 源ID
     * @param request 更新请求
     * @return 更新后的 RSS 源信息
     */
    @PUT
    @Path("/{id}")
    public RssSourceService.RssSourceResponse update(
        @PathParam("id") Long id,
        UpdateRssSourceRequest request
    ) {
        return service.update(id, request);
    }

    /**
     * 删除指定的 RSS 源。
     * @param id RSS 源ID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /**
     * 导入 OPML 文件中的 RSS 源。
     * @param file 上传的 OPML 文件
     * @return 导入结果，包含成功/失败的源列表
     */
    @POST
    @Path("/import-opml")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importOpml(@RestForm("file") FileUpload file) {
        try {
            if (file == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "请上传OPML文件")).build();
            }
            java.nio.file.Path uploadedPath = file.uploadedFile();
            Map<String, Object> result = service.importOpml(
                java.nio.file.Files.newInputStream(uploadedPath));
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "OPML导入失败: " + e.getMessage())).build();
        }
    }

    /**
     * 导出 RSS 源为 OPML 文件，可按分组筛选。
     * @param groupIds 分组ID列表（可选），为空则导出全部源
     * @return OPML XML 文件下载
     */
    @GET
    @Path("/export-opml")
    @Produces("application/xml")
    public Response exportOpml(@QueryParam("groupIds") List<Long> groupIds) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            service.exportOpml(baos, groupIds);
            return Response.ok(baos.toByteArray())
                .header("Content-Disposition", "attachment; filename=\"rss-sources.opml\"")
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "OPML导出失败")).build();
        }
    }
}
