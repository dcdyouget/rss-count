package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.service.SettingsService;

import java.util.Map;

@Path("/api/v1/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsController {

    @Inject
    SettingsService service;

    @GET
    public SettingsService.SettingsResponse get() {
        return service.get();
    }

    @PUT
    public Response update(SettingsService.UpdateSettingsRequest request) {
        try {
            var result = service.update(request);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build();
        }
    }
}
