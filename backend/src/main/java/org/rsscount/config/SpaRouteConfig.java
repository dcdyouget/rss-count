package org.rsscount.config;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SpaRouteConfig {

    void init(@Observes Router router) {
        router.get("/*").order(Integer.MAX_VALUE)
            .handler(ctx -> {
                String path = ctx.normalizedPath();
                if (!path.startsWith("/api/") && !path.startsWith("/static/")) {
                    ctx.reroute("/");
                }
            });
    }
}
