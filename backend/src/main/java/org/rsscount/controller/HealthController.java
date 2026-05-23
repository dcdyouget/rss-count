package org.rsscount.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查 REST 接口。
 * 路由前缀: /api/v1/health
 * 提供系统健康状态检查端点，用于监控和负载均衡健康探测。
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class HealthController {

    /**
     * 健康检查端点。返回服务状态、当前时间戳和版本信息。
     * @return 健康检查响应（status / timestamp / version）
     */
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
