package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.service.DraftService;

import java.util.Map;

@Path("/api/v1/drafts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DraftController {

    @Inject
    DraftService draftService;

    // ── 18. GET /drafts — List with pagination ─────────────

    @GET
    public DraftService.PaginatedResponse<DraftService.DraftListSummary> list(
        @QueryParam("page") @DefaultValue("1") int page,
        @QueryParam("size") @DefaultValue("20") int size
    ) {
        return draftService.list(page, size);
    }

    // ── 19. GET /drafts/{id} — Detail ──────────────────────

    @GET
    @Path("/{id}")
    public DraftService.DraftResponse getDetail(@PathParam("id") Long id) {
        return draftService.getById(id);
    }

    // ── 17. POST /drafts — Create ─────────────────────────

    @POST
    public Response create(DraftService.CreateDraftRequest request) {
        try {
            var result = draftService.create(request);
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ── 20. PUT /drafts/{id} — Update ──────────────────────

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, DraftService.UpdateDraftRequest request) {
        try {
            var result = draftService.update(id, request);
            return Response.ok(result).build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ── 21. DELETE /drafts/{id} — Delete ───────────────────

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

    // ── 22. POST /drafts/{id}/generate — AI generation ────

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
