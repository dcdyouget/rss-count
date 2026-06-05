package org.rsscount.config;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * 全局异常映射器，统一返回 JSON 格式的错误响应。
 *
 * 拦截所有未在控制器中捕获的异常，根据异常类型映射到合适的 HTTP 状态码，
 * 并返回结构一致的错误体：{@code {"error": "message", "status": code, "timestamp": "ISO time", "path": "request path"}}。
 *
 * SPA Fallback: 对于 /tasks、/settings 等前端路由的 404 请求，返回 index.html
 * 内容，使前端 Vue Router 可以接管路由并正确渲染页面。
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Inject
    HttpHeaders httpHeaders;

    @Context
    UriInfo uriInfo;

    /** 缓存的 index.html 内容（SPA fallback 用） */
    private static volatile String indexHtml = null;

    @Override
    public Response toResponse(Exception exception) {
        int status;
        String message;

        if (exception instanceof NotFoundException) {
            // 404 — 资源不存在；首先尝试 SPA fallback
            String path = uriInfo != null ? uriInfo.getPath() : "";
            if (!path.startsWith("api/") && !path.startsWith("/api/")
                    && !path.startsWith("static/") && !path.startsWith("/static/")
                    && !path.startsWith("assets/") && !path.startsWith("/assets/")
                    && !path.equals("") && !path.equals("/")) {
                // 非 API/静态资源路径 -> 当作 SPA 前端路由处理
                Response spaResponse = serveIndexHtml();
                if (spaResponse != null) {
                    return spaResponse;
                }
            }
            status = 404;
            message = exception.getMessage() != null ? exception.getMessage() : "资源不存在";
        } else if (exception instanceof IllegalArgumentException) {
            // 400 — 参数错误
            status = 400;
            message = exception.getMessage() != null ? exception.getMessage() : "请求参数无效";
        } else if (exception instanceof IllegalStateException) {
            // 409 — 状态冲突
            status = 409;
            message = exception.getMessage() != null ? exception.getMessage() : "资源状态冲突";
        } else if (exception instanceof WebApplicationException) {
            // 使用 WebApplicationException 自身携带的状态码
            WebApplicationException wae = (WebApplicationException) exception;
            status = wae.getResponse().getStatus();
            message = exception.getMessage() != null ? exception.getMessage() : "请求处理失败";
        } else {
            // 500 — 服务器内部错误，记录完整堆栈
            status = 500;
            message = "服务器内部错误";
            Log.error("未预期的服务器内部异常", exception);
        }

        // 非 500 异常以 WARN 级别记录简要信息
        if (status != 500) {
            Log.warnv("请求异常 [status={0}, path={1}, message={2}]",
                    status,
                    uriInfo != null ? uriInfo.getPath() : "unknown",
                    exception.getMessage());
        }

        String path = uriInfo != null ? uriInfo.getPath() : "";

        Map<String, Object> body = Map.of(
                "error", message,
                "status", status,
                "timestamp", Instant.now().toString(),
                "path", path
        );

        return Response.status(status)
                .entity(body)
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * 尝试读取 classpath 中的 index.html 并返回 200 OK 响应。
     * 读取后缓存内容，避免重复 I/O。
     */
    private Response serveIndexHtml() {
        try {
            if (indexHtml == null) {
                try (InputStream is = getClass().getResourceAsStream("/META-INF/resources/index.html")) {
                    if (is != null) {
                        indexHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            if (indexHtml != null) {
                return Response.ok(indexHtml).type(MediaType.TEXT_HTML).build();
            }
        } catch (Exception e) {
            Log.warn("SPA fallback: 无法读取 index.html", e);
        }
        return null;
    }
}
