package org.rsscount.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间规范化工具 — 将多种日期格式统一转为 UTC+8 的 LocalDateTime。
 *
 * 支持格式：
 *   - RFC 822 / RFC 1123
 *   - ISO 8601（带/不带时区、带/不带毫秒）
 *   - Unix 时间戳（秒/毫秒）
 *   - 中文日期（2026年5月20日 10:30）
 *   - 相对时间（3小时前、5分钟前、刚刚）
 *   - 无时区信息默认当 UTC+8
 */
public class TimeNormalizer {

    // 目标时区 UTC+8
    private static final ZoneId ZONE_UTC8 = ZoneId.of("+08:00");

    // ---- 相对时间 ----
    private static final Pattern RELATIVE_MINUTE = Pattern.compile("(\\d+)\\s*分钟前");
    private static final Pattern RELATIVE_HOUR = Pattern.compile("(\\d+)\\s*小时前");
    private static final Pattern RELATIVE_DAY = Pattern.compile("(\\d+)\\s*天前");
    private static final Pattern JUST_NOW = Pattern.compile("刚刚");
    private static final Pattern YESTERDAY = Pattern.compile("昨天");
    private static final Pattern DAY_BEFORE_YESTERDAY = Pattern.compile("前天");

    private TimeNormalizer() {
    }

    /**
     * 解析日期时间字符串，统一转为 UTC+8 的 LocalDateTime。
     *
     * @param raw 原始日期时间字符串，可为 null
     * @return UTC+8 的 LocalDateTime；解析失败返回 null
     */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String input = raw.trim();

        // 1. 尝试 Unix 时间戳（全数字，10-13 位）
        if (input.matches("\\d{10,13}")) {
            return parseUnixTimestamp(input);
        }

        // 2. 尝试 RFC 822 / RFC 1123
        LocalDateTime result = tryParseRfc822(input);
        if (result != null) return result;

        // 3. 尝试 ISO 8601 带时区偏移量（+HH:MM、+HHMM、+HH）
        result = tryParseIsoWithOffset(input);
        if (result != null) return result;

        // 4. 尝试 ISO 8601 带 Z 后缀（UTC）
        result = tryParseIsoZulu(input);
        if (result != null) return result;

        // 5. 尝试中文日期
        result = tryParseChineseDate(input);
        if (result != null) return result;

        // 6. 尝试 ISO 8601 / 通用日期时间（无时区，默认 UTC+8）
        result = tryParseIsoNoTz(input);
        if (result != null) return result;

        // 7. 尝试相对时间
        result = tryParseRelativeTime(input);
        if (result != null) return result;

        return null;
    }

    // ---- Unix 时间戳 ----
    private static LocalDateTime parseUnixTimestamp(String input) {
        try {
            long value = Long.parseLong(input);
            // 10 位 → 秒；13 位 → 毫秒
            if (input.length() <= 10) {
                value *= 1000;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZONE_UTC8);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    // ---- RFC 822 / RFC 1123 ----
    private static LocalDateTime tryParseRfc822(String input) {
        try {
            // Remove parenthetical timezone name suffix like " (CST)" or " (UTC)"
            String cleaned = input.replaceAll("\\s*\\([^)]*\\)\\s*$", "");

            // Try Java's built-in RFC_1123_DATE_TIME (handles most RFC 822/1123 formats)
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME);
                return zdt.withZoneSameInstant(ZONE_UTC8).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through to custom parsers
            }

            // Try custom RFC 822 with numeric offset like "+0800"
            try {
                DateTimeFormatter rfc822Numeric = new DateTimeFormatterBuilder()
                    .appendPattern("[EEE, ]d MMM yyyy HH:mm:ss")
                    .appendOffset("+HHMM", "+0000")
                    .toFormatter(Locale.ENGLISH);
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(cleaned, rfc822Numeric);
                return odt.atZoneSameInstant(ZONE_UTC8).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through
            }

            // Try with textual timezone like "CST", "EST"
            try {
                DateTimeFormatter rfc822Text = new DateTimeFormatterBuilder()
                    .appendPattern("[EEE, ]d MMM yyyy HH:mm:ss z")
                    .toFormatter(Locale.ENGLISH);
                ZonedDateTime zdt = ZonedDateTime.parse(cleaned, rfc822Text);
                return zdt.withZoneSameInstant(ZONE_UTC8).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- ISO 8601 带时区偏移量 ----
    private static LocalDateTime tryParseIsoWithOffset(String input) {
        try {
            // Try with offset like "+08:00"
            DateTimeFormatter isoOffset = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .appendPattern("[XXX][XX][X]")
                .toFormatter();
            ZonedDateTime zdt = ZonedDateTime.parse(input, isoOffset);
            return zdt.withZoneSameInstant(ZONE_UTC8).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---- ISO 8601 带 Z 后缀 ----
    private static LocalDateTime tryParseIsoZulu(String input) {
        if (!input.endsWith("Z") && !input.endsWith("z")) {
            return null;
        }
        try {
            String withoutZ = input.substring(0, input.length() - 1);
            LocalDateTime utc = LocalDateTime.parse(withoutZ, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZONE_UTC8)
                .toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---- 中文日期 ----
    private static LocalDateTime tryParseChineseDate(String input) {
        // Try various Chinese date patterns
        String[][] patterns = {
            {"yyyy年M月d日 HH:mm:ss", "datetime"},
            {"yyyy年M月d日 HH:mm", "datetime"},
            {"yyyy年MM月dd日 HH:mm:ss", "datetime"},
            {"yyyy年MM月dd日 HH:mm", "datetime"},
            {"yyyy年M月d日", "date"},
            {"yyyy年MM月dd日", "date"},
        };

        for (String[] entry : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(entry[0], Locale.CHINA);
                if ("datetime".equals(entry[1])) {
                    return LocalDateTime.parse(input, fmt);
                } else {
                    LocalDate ld = LocalDate.parse(input, fmt);
                    return LocalDateTime.of(ld, LocalTime.MIDNIGHT);
                }
            } catch (DateTimeParseException e) {
                // try next
            }
        }
        return null;
    }

    // ---- ISO 8601 / 通用日期时间（无时区，默认 UTC+8） ----
    private static LocalDateTime tryParseIsoNoTz(String input) {
        try {
            // Date only: yyyy-MM-dd
            if (input.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate ld = LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE);
                return LocalDateTime.of(ld, LocalTime.MIDNIGHT);
            }

            // DateTime without timezone: yyyy-MM-dd HH:mm:ss or yyyy-MM-ddTHH:mm:ss
            // Replace T separator with space for unified parsing
            String normalized = input.replace('T', ' ');
            DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd[ HH:mm:ss[.SSS]]")
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter(Locale.ENGLISH);
            return LocalDateTime.parse(normalized, fmt);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---- 相对时间 ----
    private static LocalDateTime tryParseRelativeTime(String input) {
        LocalDateTime now = LocalDateTime.now(ZONE_UTC8);

        // "刚刚"
        if (JUST_NOW.matcher(input).matches()) {
            return now;
        }

        // "N分钟前"
        Matcher minMatcher = RELATIVE_MINUTE.matcher(input);
        if (minMatcher.matches()) {
            int minutes = Integer.parseInt(minMatcher.group(1));
            return now.minusMinutes(minutes);
        }

        // "N小时前"
        Matcher hourMatcher = RELATIVE_HOUR.matcher(input);
        if (hourMatcher.matches()) {
            int hours = Integer.parseInt(hourMatcher.group(1));
            return now.minusHours(hours);
        }

        // "N天前"
        Matcher dayMatcher = RELATIVE_DAY.matcher(input);
        if (dayMatcher.matches()) {
            int days = Integer.parseInt(dayMatcher.group(1));
            return now.minusDays(days);
        }

        // "昨天"
        if (YESTERDAY.matcher(input).matches()) {
            return now.minusDays(1).with(LocalTime.MIDNIGHT);
        }

        // "前天"
        if (DAY_BEFORE_YESTERDAY.matcher(input).matches()) {
            return now.minusDays(2).with(LocalTime.MIDNIGHT);
        }

        return null;
    }
}
