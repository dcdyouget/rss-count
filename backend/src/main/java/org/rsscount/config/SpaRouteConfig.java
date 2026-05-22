package org.rsscount.config;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SpaRouteConfig {

    @ConfigProperty(name = "image.storage.path", defaultValue = "/app/data/img")
    String imageStoragePath;

    void init(@Observes Router router) {
        // Serve locally downloaded images
        router.route("/static/images/*").order(2)
                .handler(StaticHandler.create()
                        .setAllowRootFileSystemAccess(true)
                        .setWebRoot(imageStoragePath)
                        .setCachingEnabled(true)
                        .setCacheEntryTimeout(86400000));

        // SPA fallback: all non-API, non-static paths serve index.html
        router.get("/*").order(Integer.MAX_VALUE)
            .handler(ctx -> {
                String path = ctx.normalizedPath();
                if (!path.startsWith("/api/") && !path.startsWith("/static/")) {
                    ctx.reroute("/");
                }
            });
    }
}
