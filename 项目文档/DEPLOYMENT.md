# RSS Count 部署文档

## 开发模式

```bash
# 前端 (HMR)
cd frontend && npm install && npm run dev
# → localhost:5173

# 后端 (热重载)
cd backend && ./mvnw quarkus:dev
# → localhost:8080
```

## 生产构建

```bash
# 1. 构建前端
cd frontend && npm run build

# 2. 复制到后端
cp -r frontend/dist/* backend/src/main/resources/META-INF/resources/

# 3. 构建后端
cd backend && ./mvnw package -DskipTests

# 4. 构建 Docker 镜像
cd .. && docker build -t rss-count:latest .

# 5. 启动
docker compose up -d
```

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY backend/target/quarkus-app/ ./
RUN mkdir -p /app/data/img
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
```

- 基础镜像：eclipse-temurin:21-jre (~180MB)
- 前端静态资源嵌入 JAR（META-INF/resources/）
- 不含 GraalVM Native Image

## docker-compose.yml

```yaml
services:
  rss-count:
    image: rss-count:latest
    container_name: rss-count
    ports:
      - "8080:8080"
    volumes:
      - /root/data:/app/data
    restart: unless-stopped
```

## 数据持久化

宿主机 `/root/data/` → 容器 `/app/data/`：

| 路径 | 说明 |
|------|------|
| rsscount.db | SQLite 数据库（journal_mode=DELETE） |
| app.log | 应用日志 |
| img/ | 下载的新闻图片 |

## 核心配置（application.properties）

| 配置 | 值 | 说明 |
|------|-----|------|
| quarkus.http.port | 8080 | |
| quarkus.datasource.jdbc.url | jdbc:sqlite:data/rsscount.db?journal_mode=DELETE&busy_timeout=60000&foreign_keys=ON | |
| quarkus.datasource.jdbc.max-size | 2 | 1主+1format |
| quarkus.hibernate-orm.database.generation | update | 重启保留数据 |
| image.storage.path | /app/data/img | 图片存储 |

## SPA 路由 Fallback

```java
// SpaRouteConfig.java
router.get("/*").order(Integer.MAX_VALUE)
    .handler(ctx -> {
        if (!path.startsWith("/api/") && !path.startsWith("/static/")) {
            ctx.reroute("/");
        }
    });
```

静态图片：`router.route("/static/images/*")` → Vert.x StaticHandler 从磁盘提供

## 运维命令

```bash
# 查看日志
docker logs -f rss-count

# 备份数据库
cp /root/data/rsscount.db /root/data/backup-$(date +%Y%m%d).db

# 重启
docker compose restart

# 查看资源
docker stats rss-count

# 健康检查
curl http://localhost:8080/api/v1/health
```

## 注意事项

1. **SQLite 写锁**：max-size=2 + FORMAT_THREADS=1，写操作串行化
2. **磁盘空间**：图片本地化会占用空间，建议监控 `/root/data/img/`
3. **AI API Key**：通过 Settings 页面管理，回显脱敏
4. **定时任务**：taskIntervalHours=0 可禁用
5. **数据库模式**：DELETE journal（非 WAL），busy_timeout 可正确处理 SQLITE_BUSY
