package org.rsscount.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Map;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class HealthController {

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now().toString(),
            "version", "1.0.0-SNAPSHOT"
        )).build();
    }
}
