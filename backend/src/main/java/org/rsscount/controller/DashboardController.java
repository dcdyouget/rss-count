package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.rsscount.service.DashboardService;

import java.util.List;

/**
 * 仪表盘 REST 接口。
 * 路由前缀: /api/v1/dashboard
 * 提供系统统计数据、最近任务和最近报告等概览信息。
 */
@Path("/api/v1/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardController {

    @Inject
    DashboardService service;

    /**
     * 获取仪表盘统计数据，包括任务数、新闻数、源数等汇总信息。
     * @return 统计数据响应
     */
    @GET
    @Path("/stats")
    public DashboardService.StatsResponse getStats() {
        return service.getStats();
    }

    /**
     * 获取最近执行的任务列表。
     * @return 最近任务列表
     */
    @GET
    @Path("/recent-tasks")
    public List<DashboardService.RecentTaskItem> getRecentTasks() {
        return service.getRecentTasks();
    }

    /**
     * 获取最近生成的报告列表。
     * @return 最近报告列表
     */
    @GET
    @Path("/recent-reports")
    public List<DashboardService.RecentReportItem> getRecentReports() {
        return service.getRecentReports();
    }
}
