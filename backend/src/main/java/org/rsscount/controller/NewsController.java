package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.service.NewsService;

import java.util.Map;

@Path("/api/v1/news")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NewsController {

    @Inject
    NewsService service;

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

    @GET
    @Path("/{id}")
    public NewsService.NewsDetail getDetail(@PathParam("id") Long id) {
        return service.getDetail(id);
    }

    @PUT
    @Path("/{id}/read")
    public Response markAsRead(@PathParam("id") Long id) {
        service.markAsRead(id);
        return Response.ok(Map.of("success", true)).build();
    }

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

    @GET
    @Path("/material-pile")
    public NewsService.PagedResponse<NewsService.MaterialPileItem> materialPileList(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("50") int size
    ) {
        return service.materialPileList(page, size);
    }
}
