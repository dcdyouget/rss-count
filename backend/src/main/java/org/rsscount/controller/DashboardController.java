package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.rsscount.service.DashboardService;

import java.util.List;

@Path("/api/v1/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardController {

    @Inject
    DashboardService service;

    @GET
    @Path("/stats")
    public DashboardService.StatsResponse getStats() {
        return service.getStats();
    }

    @GET
    @Path("/recent-tasks")
    public List<DashboardService.RecentTaskItem> getRecentTasks() {
        return service.getRecentTasks();
    }

    @GET
    @Path("/recent-reports")
    public List<DashboardService.RecentReportItem> getRecentReports() {
        return service.getRecentReports();
    }
}
