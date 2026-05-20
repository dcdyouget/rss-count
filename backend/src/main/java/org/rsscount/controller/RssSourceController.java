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

@Path("/api/v1/rss-sources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RssSourceController {

    @Inject
    RssSourceService service;

    @GET
    public List<RssSourceService.RssSourceResponse> list(
        @QueryParam("groupId") Long groupId
    ) {
        return service.list(groupId);
    }

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

    @PUT
    @Path("/{id}")
    public RssSourceService.RssSourceResponse update(
        @PathParam("id") Long id,
        UpdateRssSourceRequest request
    ) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

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

    @GET
    @Path("/export-opml")
    @Produces("application/xml")
    public Response exportOpml() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            service.exportOpml(baos);
            return Response.ok(baos.toByteArray())
                .header("Content-Disposition", "attachment; filename=\"rss-sources.opml\"")
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "OPML导出失败")).build();
        }
    }
}
