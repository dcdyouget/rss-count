package org.rsscount.util;

import java.util.regex.Pattern;

/**
 * 标题清洗工具 — 用于去重前的标题标准化。
 *
 * 清洗顺序：
 *   1. 去除 HTML 标签
 *   2. 去除 Emoji
 *   3. 去除【快讯】【突发】等前缀
 *   4. 去除 [来源] 格式的媒体前缀
 *   5. 全角转半角
 *   6. 多空格压缩
 *   7. 统一小写
 *   8. 首尾空白修剪
 */
public class TextCleaner {

    // HTML 标签
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    // Emoji 范围：涵盖常见 emoji Unicode 区块
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "[\\uD800-\\uDBFF\\uDC00-\\uDFFF]" +        // Surrogate pairs (including emoji)
        "|[\\u2600-\\u27BF]" +                       // Miscellaneous Symbols
        "|[\\u2B50-\\u2B55]" +                       // Star symbols
        "|[\\uFE00-\\uFE0F]" +                       // Variation Selectors
        "|[\\u200D]" +                                // Zero Width Joiner
        "|[\\uFE0F\\u20E3]" +                         // More emoji modifiers
        "|[\\u231A-\\u231B]" +                        // Watch symbols
        "|[\\u23E9-\\u23F3]" +                        // Double triangles etc
        "|[\\u23F8-\\u23FA]" +                        // Control symbols
        "|[\\u25AA-\\u25AB\\u25B6\\u25C0\\u25FB-\\u25FE]" +
        "|[\\u2934-\\u2935]" +
        "|[\\u2B05-\\u2B07]" +
        "|[\\u3030\\u303D]" +
        "|[\\u3297\\u3299]" +
        "|[\\u203C\\u2049]" +
        "|[\\u2122\\u2139]" +
        "|[\\u2328\\u23CF]" +
        "|[\\u24C2]" +
        "|[\\u25B6\\u25C0]" +
        "|[\\u2614-\\u2615\\u2618\\u261D]" +
        "|[\\u2620\\u2622-\\u2623\\u2626\\u262A\\u262E-\\u262F]" +
        "|[\\u2638-\\u263A\\u2640\\u2642\\u2648-\\u2653\\u265F-\\u2660]" +
        "|[\\u2663\\u2665-\\u2666\\u2668\\u267B\\u267E-\\u267F]" +
        "|[\\u2692-\\u2697\\u2699\\u269B-\\u269C\\u26A0-\\u26A1\\u26A7]" +
        "|[\\u26AA-\\u26AB\\u26B0-\\u26B1\\u26BD-\\u26BE\\u26C4-\\u26C5]" +
        "|[\\u26C8\\u26CE-\\u26CF\\u26D1\\u26D3-\\u26D4\\u26E9-\\u26EA]" +
        "|[\\u26F0-\\u26F5\\u26F7-\\u26FA\\u26FD\\u2702\\u2705]" +
        "|[\\u2708-\\u270D\\u270F\\u2712\\u2714\\u2716\\u271D\\u2721]" +
        "|[\\u2728\\u2733-\\u2734\\u2744\\u2747\\u274C\\u274E\\u2753-\\u2755]" +
        "|[\\u2757\\u2763-\\u2764\\u2795-\\u2797\\u27A1\\u27B0\\u27BF]" +
        "|[\\uD83C\\uDF00-\\uDFFF]" +                 // Misc Symbols and Pictographs
        "|[\\uD83D\\uDC00-\\uDE4F]" +                 // Emoticons
        "|[\\uD83D\\uDE80-\\uDEFF]" +                 // Transport and Map Symbols
        "|[\\uD83E\\uDD00-\\uDDFF]"                   // Supplemental Symbols and Pictographs
    );

    // 前缀模式：【快讯】【突发】【独家】【滚动】等
    private static final Pattern PREFIX_PATTERN = Pattern.compile(
        "^[【\\[][^】\\]]*[】\\]]\\s*"
    );

    // 多空格
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private TextCleaner() {
    }

    /**
     * 清洗标题文本。
     *
     * @param raw 原始文本，可为 null
     * @return 清洗后的文本；null 或空串返回空字符串
     */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String result = raw;

        // 1. 去除 HTML 标签
        result = HTML_TAG_PATTERN.matcher(result).replaceAll("");

        // 2. 去除 Emoji
        result = EMOJI_PATTERN.matcher(result).replaceAll("");

        // 3. 去除【快讯】等前缀（循环去除可能嵌套的多个前缀）
        String prev;
        do {
            prev = result;
            result = PREFIX_PATTERN.matcher(result).replaceFirst("");
        } while (!result.equals(prev));

        // 4. 全角转半角
        result = fullWidthToHalfWidth(result);

        // 5. 多空格压缩为单个空格
        result = MULTI_SPACE_PATTERN.matcher(result).replaceAll(" ");

        // 6. 统一小写
        result = result.toLowerCase();

        // 7. 首尾空白修剪
        result = result.trim();

        return result;
    }

    /**
     * 全角字符转半角（ASCII 范围）。
     * 全角字符范围：U+FF01-FF5E → 半角 U+0021-007E
     * 全角空格 U+3000 → 半角空格 U+0020
     */
    private static String fullWidthToHalfWidth(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '　') {
                // 全角空格 → 半角空格
                sb.append(' ');
            } else if (ch >= '！' && ch <= '～') {
                // 全角标点/字母/数字 → 半角
                sb.append((char) (ch - 0xFEE0));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
