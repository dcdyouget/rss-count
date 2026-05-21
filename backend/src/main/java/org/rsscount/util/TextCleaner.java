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

    // Emoji 检测使用 Java Character API 逐码点判断，避免超大正则导致编译崩溃

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

        // 2. 去除 Emoji（逐码点检测，避免超大正则编译崩溃）
        StringBuilder sb = new StringBuilder(result.length());
        for (int i = 0; i < result.length(); ) {
            int cp = result.codePointAt(i);
            if (isEmoji(cp)) {
                i += Character.charCount(cp);
            } else {
                sb.appendCodePoint(cp);
                i += Character.charCount(cp);
            }
        }
        result = sb.toString();

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
     * 判断 Unicode 码点是否为 Emoji 字符。
     * 使用 Character 类型 + Unicode 区块范围检测，避免超大规模正则表达式。
     */
    private static boolean isEmoji(int codePoint) {
        int type = Character.getType(codePoint);
        // 代理对（surrogate pair 的高位/低位，这些不属于合法独立码点但安全过滤）
        if (type == Character.SURROGATE) {
            return true;
        }
        // 其他符号（OTHER_SYMBOL 包含大部分 emoji）
        if (type == Character.OTHER_SYMBOL) {
            return true;
        }
        // 修饰符符号（如肤色修饰符）
        if (type == Character.MODIFIER_SYMBOL) {
            return true;
        }
        // 明确涵盖一些常见 emoji 区块作为备保
        return (codePoint >= 0x1F300 && codePoint <= 0x1F9FF)   // Misc Symbols, Emoticons, Suppl. Symbols
            || (codePoint >= 0x2600 && codePoint <= 0x27BF)     // Misc Symbols, Dingbats
            || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)     // Variation Selectors
            || codePoint == 0x200D                               // Zero Width Joiner
            || (codePoint >= 0xE0020 && codePoint <= 0xE007F);   // Tags (用于 flag 序列)
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
