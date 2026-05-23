# RSS Count — 极简部署镜像
# 前端和后端已在宿主机编译完成，直接复制

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY backend/target/quarkus-app/ ./
RUN mkdir -p /app/data/img
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "quarkus-run.jar"]
