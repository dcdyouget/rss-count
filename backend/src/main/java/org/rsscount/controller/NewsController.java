package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.service.NewsService;

import java.util.Map;

/**
 * 新闻管理 REST 接口。
 * 路由前缀: /api/v1/news
 * 负责新闻的查询、详情查看、标记已读、素材堆管理等操作。
 */
@Path("/api/v1/news")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NewsController {

    @Inject
    NewsService service;

    /**
     * 分页查询新闻列表，支持关键词搜索、报告名称筛选和已读状态筛选。
     * @param page 页码（1-based）
     * @param size 每页条数，默认20
     * @param keyword 搜索关键词（可选）
     * @param reportName 报告名称筛选（可选）
     * @param isRead 已读状态筛选（可选）
     * @return 分页的新闻列表
     */
    @GET
    public NewsService.PagedResponse<NewsService.NewsListItem> list(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("20") int size,
        @QueryParam("keyword") String keyword,
        @QueryParam("reportName") String reportName,
        @QueryParam("isRead") Boolean isRead
    ) {
        return service.list(page, size, keyword, reportName, isRead);
    }

    /**
     * 获取指定新闻的详细信息。
     * @param id 新闻ID
     * @return 新闻详情
     * @throws NotFoundException 新闻不存在时抛出
     */
    @GET
    @Path("/{id}")
    public NewsService.NewsDetail getDetail(@PathParam("id") Long id) {
        return service.getDetail(id);
    }

    /**
     * 将指定新闻标记为已读。
     * @param id 新闻ID
     * @return 操作结果
     */
    @PUT
    @Path("/{id}/read")
    public Response markAsRead(@PathParam("id") Long id) {
        service.markAsRead(id);
        return Response.ok(Map.of("success", true)).build();
    }

    /**
     * 批量将新闻移入或移出素材堆。
     * @param request 批量操作请求，包含新闻ID列表和目标状态
     * @return 操作结果
     */
    @POST
    @Path("/batch-material-pile")
    public Response batchMaterialPile(NewsService.BatchMaterialPileRequest request) {
        try {
            var result = service.batchMaterialPile(request);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * 分页查询素材堆中的新闻列表。
     * @param page 页码（1-based）
     * @param size 每页条数，默认50
     * @return 分页的素材堆新闻列表
     */
    @GET
    @Path("/material-pile")
    public NewsService.PagedResponse<NewsService.MaterialPileItem> materialPileList(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("50") int size
    ) {
        return service.materialPileList(page, size);
    }
}
