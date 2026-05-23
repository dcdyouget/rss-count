package org.rsscount.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.rsscount.service.SettingsService;

import java.util.Map;

/**
 * 系统设置 REST 接口。
 * 路由前缀: /api/v1/settings
 * 负责系统全局配置的读取和更新。
 */
@Path("/api/v1/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsController {

    @Inject
    SettingsService service;

    /**
     * 获取当前系统设置。
     * @return 系统设置详情
     */
    @GET
    public SettingsService.SettingsResponse get() {
        return service.get();
    }

    /**
     * 更新系统设置。
     * @param request 更新设置请求体
     * @return 更新后的系统设置
     */
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
