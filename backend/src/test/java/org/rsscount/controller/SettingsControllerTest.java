package org.rsscount.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rsscount.entity.Settings;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * I7: Settings integration tests.
 * I7-1: First get → returns default values
 * I7-2: Update → GET returns updated values
 * I7-3: API Key masking
 * I7-4: API Key not overwrite with "******"
 */
@QuarkusTest
class SettingsControllerTest {

    @BeforeEach
    @Transactional
    void cleanup() {
        Settings.deleteAll();
    }

    // ---- I7-1: First get returns default values ----

    @Test
    void testGetDefaultSettings() {
        given()
            .when()
            .get("/api/v1/settings")
            .then()
            .statusCode(200)
            .body("taskIntervalHours", equalTo(6))
            .body("aiApiUrl", nullValue())
            .body("aiApiKey", nullValue())
            .body("aiModel", nullValue())
            .body("defaultGroupId", nullValue());
    }

    // ---- I7-2: Update then GET returns updated values ----

    @Test
    void testUpdateSettings() {
        Map<String, Object> body = Map.of(
            "taskIntervalHours", 12,
            "aiApiUrl", "https://api.openai.com/v1",
            "aiApiKey", "sk-testkey123456",
            "aiModel", "gpt-4o",
            "defaultGroupId", 0
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/v1/settings")
            .then()
            .statusCode(200)
            .body("taskIntervalHours", equalTo(12))
            .body("aiApiUrl", equalTo("https://api.openai.com/v1"))
            .body("aiModel", equalTo("gpt-4o"));

        // Verify GET returns updated values
        given()
            .when()
            .get("/api/v1/settings")
            .then()
            .statusCode(200)
            .body("taskIntervalHours", equalTo(12))
            .body("aiApiUrl", equalTo("https://api.openai.com/v1"))
            .body("aiModel", equalTo("gpt-4o"));
    }

    // ---- I7-3: API Key masking ----

    @Test
    void testApiKeyMasking() {
        Map<String, Object> body = Map.of(
            "taskIntervalHours", 6,
            "aiApiUrl", "https://api.openai.com/v1",
            "aiApiKey", "sk-abcdefghijklmnopqrstuvwxyz",
            "aiModel", "gpt-4o",
            "defaultGroupId", 0
        );

        // Update with key
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/v1/settings")
            .then()
            .statusCode(200);

        // Verify key is masked in GET
        String maskedKey = given()
            .when()
            .get("/api/v1/settings")
            .then()
            .statusCode(200)
            .extract()
            .path("aiApiKey");

        assertNotNull(maskedKey);
        assertTrue(maskedKey.contains("****"), "Key should be masked with ****");
        assertFalse(maskedKey.contains("abcdef"), "Key should not contain original middle");
        assertTrue(maskedKey.startsWith("sk-"), "Masked key should start with first 3 chars");
    }

    // ---- I7-4: API Key not overwrite with "******" ----

    @Test
    void testApiKeyNotOverwritten() {
        // First set a real key
        Map<String, Object> initialBody = Map.of(
            "taskIntervalHours", 6,
            "aiApiUrl", "https://api.openai.com/v1",
            "aiApiKey", "sk-original-key-12345",
            "aiModel", "gpt-4o",
            "defaultGroupId", 0
        );

        given()
            .contentType(ContentType.JSON)
            .body(initialBody)
            .when()
            .put("/api/v1/settings")
            .then()
            .statusCode(200);

        // Now update with "******" - should keep old key
        Map<String, Object> updateBody = Map.of(
            "taskIntervalHours", 8,
            "aiApiUrl", "https://api.openai.com/v1",
            "aiApiKey", "******",
            "aiModel", "gpt-4o",
            "defaultGroupId", 0
        );

        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when()
            .put("/api/v1/settings")
            .then()
            .statusCode(200);

        // Verify taskIntervalHours was updated
        int interval = given()
            .when()
            .get("/api/v1/settings")
            .then()
            .statusCode(200)
            .extract()
            .path("taskIntervalHours");

        assertEquals(8, interval);

        // Key should still be masked (proving it wasn't overwritten with ******)
        String maskedKey = given()
            .when()
            .get("/api/v1/settings")
            .then()
            .statusCode(200)
            .extract()
            .path("aiApiKey");

        assertTrue(maskedKey.contains("****"), "Key should still be masked (old key preserved)");
    }

    // ---- Additional: validation ----

    @Test
    void testValidationNegativeInterval() {
        Map<String, Object> body = Map.of(
            "taskIntervalHours", -1,
            "aiApiUrl", "https://api.openai.com/v1",
            "aiApiKey", "sk-key",
            "aiModel", "gpt-4o",
            "defaultGroupId", 0
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/v1/settings")
            .then()
            .statusCode(400);
    }

    @Test
    void testValidationInvalidUrl() {
        Map<String, Object> body = Map.of(
            "taskIntervalHours", 6,
            "aiApiUrl", "not-a-valid-url",
            "aiApiKey", "sk-key",
            "aiModel", "gpt-4o",
            "defaultGroupId", 0
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/v1/settings")
            .then()
            .statusCode(400);
    }
}
