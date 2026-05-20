package org.rsscount.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("U2 - TextCleaner Title Cleaning")
class TextCleanerTest {

    @Test
    @DisplayName("U2-1: Remove HTML tags")
    void removeHtmlTags() {
        assertEquals("重磅消息来了", TextCleaner.clean("<b>重磅</b>消息来了"));
        assertEquals("消息", TextCleaner.clean("<div class='news'><p>消息</p></div>"));
        assertEquals("标题", TextCleaner.clean("<a href='#'>标题</a>"));
    }

    @Test
    @DisplayName("U2-2: Remove emojis")
    void removeEmojis() {
        assertEquals("突发新闻", TextCleaner.clean("🔥突发新闻🔥"));
        assertEquals("你好世界", TextCleaner.clean("😀你好😊世界🌍"));
        assertEquals("新闻", TextCleaner.clean("📢新闻"));
    }

    @Test
    @DisplayName("U2-3: Remove 【快讯】 prefixes")
    void removeKuaiXunPrefix() {
        assertEquals("某事件发生", TextCleaner.clean("【快讯】某事件发生"));
        assertEquals("重大事件", TextCleaner.clean("【突发】重大事件"));
        assertEquals("独家新闻", TextCleaner.clean("【独家】独家新闻"));
        assertEquals("滚动消息", TextCleaner.clean("【滚动】滚动消息"));
    }

    @Test
    @DisplayName("U2-4: Full-width to half-width conversion")
    void fullWidthToHalfWidth() {
        // Note: pipeline lowercases, so expected output is lowercase
        assertEquals("abc123", TextCleaner.clean("ＡＢＣ１２３"));
        assertEquals("hello world!", TextCleaner.clean("Ｈｅｌｌｏ　Ｗｏｒｌｄ！"));
    }

    @Test
    @DisplayName("U2-5: Compress multiple spaces")
    void compressMultipleSpaces() {
        assertEquals("hello world", TextCleaner.clean("hello    world"));
        assertEquals("a b c", TextCleaner.clean("a  b   c"));
    }

    @Test
    @DisplayName("U2-6: Unify to lower case")
    void unifyToLowerCase() {
        assertEquals("hello world", TextCleaner.clean("Hello World"));
        assertEquals("abc news", TextCleaner.clean("ABC News"));
    }

    @Test
    @DisplayName("U2-7: Mixed text comprehensive test")
    void mixedText() {
        String result = TextCleaner.clean("<p>🔥【快讯】  ＡI　发布</p>");
        assertEquals("ai 发布", result);
    }

    @Test
    @DisplayName("U2-8: null and empty string handling")
    void nullAndEmptyHandling() {
        assertEquals("", TextCleaner.clean(""));
        assertEquals("", TextCleaner.clean(null));
        assertEquals("", TextCleaner.clean("   "));
    }

    @Test
    @DisplayName("Trim whitespace")
    void trimWhitespace() {
        assertEquals("标题", TextCleaner.clean("  标题  "));
        assertEquals("text", TextCleaner.clean("\t text \n"));
    }

    @Test
    @DisplayName("Remove media source prefixes like [36氪]")
    void removeMediaPrefix() {
        assertEquals("科技新闻", TextCleaner.clean("[36氪]科技新闻"));
        assertEquals("新闻内容", TextCleaner.clean("[人民日报]新闻内容"));
    }
}
