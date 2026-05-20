package org.rsscount.controller;

import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rsscount.entity.RssGroup;
import org.rsscount.entity.RssSource;
import org.rsscount.entity.RssSourceGroup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * I5: RSS Source CRUD integration tests.
 * I5-1: Add source → 201
 * I5-2: URL duplicate → 409
 * I5-3: Soft delete → isActive=false
 * I5-4: OPML import
 * I5-5: OPML export
 */
@QuarkusTest
class RssSourceControllerTest {

    @BeforeEach
    @Transactional
    void cleanup() {
        RssSourceGroup.deleteAll();
        RssSource.deleteAll();
        RssGroup.deleteAll();
    }

    // ---- I5-1: Add source ----

    @Test
    void testAddSource() {
        Map<String, Object> body = Map.of(
            "url", "https://example.com/rss",
            "name", "测试源",
            "groupIds", List.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201)
            .body("url", equalTo("https://example.com/rss"))
            .body("name", notNullValue())
            .body("isActive", equalTo(true));
    }

    @Test
    void testAddSourceWithoutName() {
        Map<String, Object> body = Map.of(
            "url", "https://example2.com/feed",
            "groupIds", List.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201)
            .body("url", equalTo("https://example2.com/feed"))
            .body("name", notNullValue());
    }

    // ---- I5-2: URL duplicate → 409 ----

    @Test
    void testAddDuplicateUrl() {
        // Create first source via REST
        Map<String, Object> body1 = Map.of(
            "url", "https://dup.example.com/rss",
            "name", "重复源",
            "groupIds", List.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(body1)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201);

        // Try to create duplicate
        Map<String, Object> body2 = Map.of(
            "url", "https://dup.example.com/rss",
            "name", "重复源2",
            "groupIds", List.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(body2)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(409)
            .body("error", containsString("已存在"));
    }

    // ---- I5-3: Soft delete ----

    @Test
    void testSoftDelete() {
        // Create source via REST
        Map<String, Object> body = Map.of(
            "url", "https://delete-test.example.com/rss",
            "name", "待删除源",
            "groupIds", List.of()
        );

        int sourceId = given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Verify it's in the list before delete
        given()
            .when()
            .get("/api/v1/rss-sources")
            .then()
            .statusCode(200)
            .body("find { it.id == " + sourceId + " }", notNullValue());

        // Delete (soft)
        given()
            .when()
            .delete("/api/v1/rss-sources/" + sourceId)
            .then()
            .statusCode(204);

        // Verify it's NOT in the list after soft delete
        given()
            .when()
            .get("/api/v1/rss-sources")
            .then()
            .statusCode(200)
            .body("find { it.id == " + sourceId + " }", nullValue());
    }

    // ---- I5-4: OPML import ----

    @Test
    void testOpmlImport() {
        String opmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test OPML</title></head>
              <body>
                <outline text="科技">
                  <outline text="36氪" xmlUrl="https://36kr.com/feed" type="rss"/>
                  <outline text="机器之心" xmlUrl="https://jiqizhixin.com/rss" type="rss"/>
                </outline>
                <outline text="创投">
                  <outline text="投资界" xmlUrl="https://pedaily.cn/rss" type="rss"/>
                </outline>
              </body>
            </opml>
            """;

        given()
            .contentType(ContentType.MULTIPART)
            .multiPart("file", "test.opml", opmlContent.getBytes(), "text/xml")
            .when()
            .post("/api/v1/rss-sources/import-opml")
            .then()
            .statusCode(200)
            .body("created", greaterThanOrEqualTo(0))
            .body("total", equalTo(3));
    }

    // ---- I5-5: OPML export ----

    @Test
    void testOpmlExport() {
        // Create group and source via REST
        Map<String, Object> groupBody = Map.of("name", "导出测试分组");
        given()
            .contentType(ContentType.JSON)
            .body(groupBody)
            .when()
            .post("/api/v1/rss-groups")
            .then()
            .statusCode(201);

        Map<String, Object> sourceBody = Map.of(
            "url", "https://export-test.example.com/rss",
            "name", "导出测试源",
            "groupIds", List.of()
        );
        given()
            .contentType(ContentType.JSON)
            .body(sourceBody)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201);

        String xml = given()
            .when()
            .get("/api/v1/rss-sources/export-opml")
            .then()
            .statusCode(200)
            .header("Content-Disposition", containsString("attachment"))
            .contentType(startsWith("application/xml"))
            .extract()
            .asString();

        assertTrue(xml.contains("<?xml"));
        assertTrue(xml.contains("<opml"));
    }

    // ---- Additional: List sources ----

    @Test
    void testListSources() {
        // Create sources via REST
        Map<String, Object> body1 = Map.of(
            "url", "https://list1.example.com/rss",
            "name", "列表源1",
            "groupIds", List.of()
        );
        given()
            .contentType(ContentType.JSON)
            .body(body1)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201);

        Map<String, Object> body2 = Map.of(
            "url", "https://list2.example.com/rss",
            "name", "列表源2",
            "groupIds", List.of()
        );
        given()
            .contentType(ContentType.JSON)
            .body(body2)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201);

        given()
            .when()
            .get("/api/v1/rss-sources")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2));
    }

    @Test
    void testUpdateSource() {
        // Create source via REST
        Map<String, Object> createBody = Map.of(
            "url", "https://update-test.example.com/rss",
            "name", "更新前名称",
            "groupIds", List.of()
        );
        int sourceId = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when()
            .post("/api/v1/rss-sources")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        Map<String, Object> updateBody = Map.of(
            "name", "更新后名称",
            "groupIds", List.of()
        );

        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when()
            .put("/api/v1/rss-sources/" + sourceId)
            .then()
            .statusCode(200)
            .body("name", equalTo("更新后名称"));
    }
}
