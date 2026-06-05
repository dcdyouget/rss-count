# RSS Count — Production Docker Image
# Frontend + backend built on host, artifacts copied into image

FROM eclipse-temurin:21-jre

# Install runtime deps + curl for HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends libwebp7 curl && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /app/data

WORKDIR /app
COPY backend/target/quarkus-app/ ./

# Security: run as non-root
RUN useradd -r -m rsscount && chown -R rsscount:rsscount /app
USER rsscount

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -sf http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "quarkus-run.jar"]
