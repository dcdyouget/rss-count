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

@Path("/api/v1/rss-groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RssGroupController {

    @Inject
    RssGroupService service;

    @GET
    public List<RssGroupService.RssGroupResponse> list() {
        return service.list();
    }

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

    @PUT
    @Path("/{id}")
    public RssGroupService.RssGroupResponse update(
        @PathParam("id") Long id,
        UpdateGroupRequest request
    ) {
        return service.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

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
