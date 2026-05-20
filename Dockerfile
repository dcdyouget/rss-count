# ============================================
# RSS Count — JVM 部署镜像
# 构建: docker build -t rss-count .
# 运行: docker compose up -d
# ============================================

# Stage 1: 构建前端
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: 构建后端 JVM (使用 Maven 镜像，自带 mvn)
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -DskipTests -q || true
COPY backend/src/ ./src/
COPY --from=frontend /app/frontend/dist/ ./src/main/resources/META-INF/resources/
RUN mvn package -DskipTests -q

# Stage 3: 运行时
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/quarkus-app/ ./
RUN mkdir -p /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
