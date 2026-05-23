# RSS Count — 极简部署镜像
# 前端和后端已在宿主机编译完成，直接复制

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY backend/target/quarkus-app/ ./
RUN apt-get update && apt-get install -y libwebp7 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "quarkus-run.jar"]
