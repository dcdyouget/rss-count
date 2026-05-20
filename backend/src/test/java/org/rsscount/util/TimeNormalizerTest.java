package org.rsscount.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("U5 - TimeNormalizer")
class TimeNormalizerTest {

    @Test
    @DisplayName("U5-1: Parse RFC 822 date format")
    void parseRfc822() {
        LocalDateTime result = TimeNormalizer.parse("Tue, 20 May 2026 10:30:00 +0800");
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());
        assertEquals(0, result.getSecond());
    }

    @Test
    @DisplayName("U5-2: Parse ISO 8601 with timezone")
    void parseIso8601WithTimezone() {
        LocalDateTime result = TimeNormalizer.parse("2026-05-20T10:30:00+08:00");
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());

        // ISO 8601 UTC (Z)
        LocalDateTime utcResult = TimeNormalizer.parse("2026-05-20T02:30:00Z");
        assertNotNull(utcResult);
        // UTC 02:30 → UTC+8 10:30
        assertEquals(10, utcResult.getHour());
        assertEquals(30, utcResult.getMinute());
    }

    @Test
    @DisplayName("U5-3: Parse Unix millisecond timestamp")
    void parseUnixMillisecondTimestamp() {
        // 2026-05-20 10:30:00 UTC+8 = 2026-05-20 02:30:00 UTC
        // Unix epoch millis for that
        long millis = java.time.ZonedDateTime
            .of(2026, 5, 20, 10, 30, 0, 0, java.time.ZoneId.of("+08:00"))
            .toInstant().toEpochMilli();

        LocalDateTime result = TimeNormalizer.parse(String.valueOf(millis));
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());

        // Seconds timestamp
        long seconds = millis / 1000;
        LocalDateTime resultSec = TimeNormalizer.parse(String.valueOf(seconds));
        assertNotNull(resultSec);
        assertEquals(2026, resultSec.getYear());
        assertEquals(5, resultSec.getMonthValue());
        assertEquals(20, resultSec.getDayOfMonth());
    }

    @Test
    @DisplayName("U5-4: Parse Chinese date format")
    void parseChineseDateFormat() {
        LocalDateTime result = TimeNormalizer.parse("2026年5月20日 10:30");
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());

        // Without time
        LocalDateTime dateOnly = TimeNormalizer.parse("2026年5月20日");
        assertNotNull(dateOnly);
        assertEquals(2026, dateOnly.getYear());
        assertEquals(5, dateOnly.getMonthValue());
        assertEquals(20, dateOnly.getDayOfMonth());
        assertEquals(0, dateOnly.getHour());
        assertEquals(0, dateOnly.getMinute());
    }

    @Test
    @DisplayName("U5-5: Parse relative time expressions")
    void parseRelativeTime() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("+08:00"));

        // "3小时前"
        LocalDateTime threeHoursAgo = TimeNormalizer.parse("3小时前");
        assertNotNull(threeHoursAgo);
        assertEquals(now.minusHours(3).getHour(), threeHoursAgo.getHour());

        // "5分钟前"
        LocalDateTime fiveMinAgo = TimeNormalizer.parse("5分钟前");
        assertNotNull(fiveMinAgo);
        assertTrue(fiveMinAgo.isAfter(now.minusMinutes(6)));

        // "2天前"
        LocalDateTime twoDaysAgo = TimeNormalizer.parse("2天前");
        assertNotNull(twoDaysAgo);
        assertEquals(now.minusDays(2).getDayOfMonth(), twoDaysAgo.getDayOfMonth());

        // "刚刚"
        LocalDateTime justNow = TimeNormalizer.parse("刚刚");
        assertNotNull(justNow);
        assertTrue(justNow.isAfter(now.minusMinutes(1)));
    }

    @Test
    @DisplayName("U5-6: Unparseable input returns null")
    void unparseableReturnsNull() {
        assertNull(TimeNormalizer.parse("not a date"));
        assertNull(TimeNormalizer.parse(""));
        assertNull(TimeNormalizer.parse(null));
        assertNull(TimeNormalizer.parse("abc123"));
    }

    @Test
    @DisplayName("Parse ISO 8601 without timezone (default UTC+8)")
    void parseIso8601NoTimezone() {
        LocalDateTime result = TimeNormalizer.parse("2026-05-20 10:30:00");
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());
    }

    @Test
    @DisplayName("Parse ISO 8601 date only")
    void parseIso8601DateOnly() {
        LocalDateTime result = TimeNormalizer.parse("2026-05-20");
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(20, result.getDayOfMonth());
    }
}
