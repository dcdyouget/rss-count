package org.rsscount.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * U4: ContentExtractor tests for the HTML clean() method.
 *
 * U4-1: HTML tags (p, b, img) are preserved through the safelist.
 * U4-2: style/class/id attributes are stripped from output.
 * U4-3: a tags have enforced target="_blank" and rel="noopener noreferrer".
 * U4-4: script and iframe elements are removed entirely.
 * U4-5: Relative URLs are converted to absolute using the base URI.
 * U4-6: Empty or null input returns null.
 * U4-7: Noise elements (nav, .ad, #sidebar, etc.) are removed.
 */
@QuarkusTest
class ContentExtractorTest {

    @Inject
    ContentExtractor extractor;

    // ── U4-1: HTML tags preserved ────────────────────────────

    @Test
    void testPreservesBasicHtmlTags() {
        String html = "<html><body><p>段落文字</p><b>加粗</b><i>斜体</i></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<p>"), "Should preserve <p> tag");
        assertTrue(result.contains("<b>"), "Should preserve <b> tag");
        assertTrue(result.contains("<i>"), "Should preserve <i> tag");
        assertTrue(result.contains("段落文字"), "Should preserve text content");
        assertTrue(result.contains("加粗"), "Should preserve bold text");
    }

    @Test
    void testPreservesImages() {
        String html = "<html><body><img src='https://example.com/pic.jpg' alt='图片' /></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<img"), "Should preserve <img> tag");
        assertTrue(result.contains("src=\"https://example.com/pic.jpg\""), "Should preserve image src");
    }

    @Test
    void testPreservesLinks() {
        String html = "<html><body><a href='https://example.com'>链接文字</a></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<a"), "Should preserve <a> tag");
        assertTrue(result.contains("链接文字"), "Should preserve link text");
    }

    @Test
    void testPreservesHeadings() {
        String html = "<html><body><h1>大标题</h1><h2>中标题</h2><h3>小标题</h3></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<h1>"), "Should preserve <h1>");
        assertTrue(result.contains("<h2>"), "Should preserve <h2>");
        assertTrue(result.contains("<h3>"), "Should preserve <h3>");
        assertTrue(result.contains("大标题"), "Should preserve heading text");
    }

    @Test
    void testPreservesBlockquotes() {
        String html = "<html><body><blockquote>引用文字</blockquote></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<blockquote>"), "Should preserve <blockquote>");
        assertTrue(result.contains("引用文字"), "Should preserve blockquote text");
    }

    @Test
    void testPreservesLists() {
        String html = "<html><body><ul><li>项一</li><li>项二</li></ul><ol><li>第一步</li></ol></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<ul>"), "Should preserve <ul>");
        assertTrue(result.contains("<ol>"), "Should preserve <ol>");
        assertTrue(result.contains("<li>"), "Should preserve <li>");
        assertTrue(result.contains("项一"), "Should preserve list item text");
    }

    @Test
    void testPreservesCodeAndPre() {
        String html = "<html><body><pre><code>public class Hello {}</code></pre></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("<pre>") || result.contains("<code>"),
            "Should preserve <pre> or <code>");
    }

    // ── U4-2: style/class/id stripped ────────────────────────

    @Test
    void testStripsStyleAttribute() {
        String html = "<html><body><p style='color:red'>带样式的文字</p></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("style="), "style attributes should be stripped");
        assertTrue(result.contains("带样式的文字"), "Text content should remain");
    }

    @Test
    void testStripsClassAttribute() {
        String html = "<html><body><p class='highlight'>带class的文字</p></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("class="), "class attributes should be stripped");
    }

    @Test
    void testStripsIdAttribute() {
        String html = "<html><body><div id='main'>带id的文字</div></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("id="), "id attributes should be stripped");
    }

    // ── U4-3: a tags have enforced attributes ─────────────────

    @Test
    void testLinkHasTargetBlank() {
        String html = "<html><body><a href='https://example.com'>链接</a></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("target=\"_blank\""),
            "Link should have target=\"_blank\"");
    }

    @Test
    void testLinkHasRelNoopener() {
        String html = "<html><body><a href='https://example.com'>链接</a></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertTrue(result.contains("rel=\"noopener noreferrer\""),
            "Link should have rel=\"noopener noreferrer\"");
    }

    // ── U4-4: script/iframe removed ──────────────────────────

    @Test
    void testRemovesScriptTags() {
        String html = "<html><body><script>alert('xss');</script><p>正文</p></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("<script>"), "script tags should be removed");
        assertFalse(result.contains("alert"), "script content should be removed");
        assertTrue(result.contains("正文"), "non-script content should remain");
    }

    @Test
    void testRemovesIframeTags() {
        String html = "<html><body><iframe src='https://evil.com'></iframe><p>正文</p></body></html>";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("<iframe>"), "iframe tags should be removed");
        assertTrue(result.contains("正文"), "non-iframe content should remain");
    }

    // ── U4-5: relative URLs converted to absolute ────────────

    @Test
    void testRelativeImageUrlBecomesAbsolute() {
        String html = "<html><body><img src='/images/pic.jpg' alt='图' /></body></html>";
        String result = extractor.clean(html, "https://example.com");

        assertNotNull(result);
        assertTrue(result.contains("src=\"https://example.com/images/pic.jpg\""),
            "Relative image src should be resolved to absolute URL");
    }

    @Test
    void testAbsoluteImageUrlStaysUnchanged() {
        String html = "<html><body><img src='https://other.com/pic.jpg' alt='图' /></body></html>";
        String result = extractor.clean(html, "https://example.com");

        assertNotNull(result);
        assertTrue(result.contains("src=\"https://other.com/pic.jpg\""),
            "Absolute image src should remain unchanged");
    }

    @Test
    void testRelativeLinkUrlBecomesAbsolute() {
        String html = "<html><body><a href='/article/123'>文章</a></body></html>";
        String result = extractor.clean(html, "https://example.com");

        assertNotNull(result);
        assertTrue(result.contains("href=\"https://example.com/article/123\""),
            "Relative link href should be resolved to absolute URL");
    }

    // ── U4-6: null/empty input returns null ──────────────────

    @Test
    void testNullInputReturnsNull() {
        assertNull(extractor.clean(null, null));
        assertNull(extractor.clean(null, "https://example.com"));
    }

    @Test
    void testEmptyInputReturnsNull() {
        assertNull(extractor.clean("", null));
        assertNull(extractor.clean("   ", null));
    }

    // ── U4-7: noise elements removed ─────────────────────────

    @Test
    void testRemovesNavigation() {
        String html = """
            <html><body>
            <nav>导航栏</nav>
            <p>正文内容</p>
            </body></html>""";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("导航栏"), "nav content should be removed");
        assertTrue(result.contains("正文内容"), "main content should remain");
    }

    @Test
    void testRemovesAdElements() {
        String html = """
            <html><body>
            <div class="ad">广告内容</div>
            <div class="sidebar">侧边栏</div>
            <p>正文</p>
            </body></html>""";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("广告内容"), "ad content should be removed");
        assertFalse(result.contains("侧边栏"), "sidebar content should be removed");
        assertTrue(result.contains("正文"), "main content should remain");
    }

    @Test
    void testRemovesCommentsSection() {
        String html = """
            <html><body>
            <div class="comments">评论区域</div>
            <div id="comments">更多评论</div>
            <div class="share">分享按钮</div>
            <p>正文内容</p>
            </body></html>""";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("评论区域"), "comment section should be removed");
        assertFalse(result.contains("更多评论"), "comment #id section should be removed");
        assertFalse(result.contains("分享按钮"), "share section should be removed");
        assertTrue(result.contains("正文内容"), "main content should remain");
    }

    @Test
    void testRemovesFooterAndHeader() {
        String html = """
            <html><body>
            <header>页眉</header>
            <footer>页脚</footer>
            <p>正文</p>
            </body></html>""";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("页眉"), "header content should be removed");
        assertFalse(result.contains("页脚"), "footer content should be removed");
    }

    @Test
    void testRemovesFormAndButton() {
        String html = """
            <html><body>
            <form><input type="text" /></form>
            <button>点击</button>
            <p>正文</p>
            </body></html>""";
        String result = extractor.clean(html, null);

        assertNotNull(result);
        assertFalse(result.contains("form"), "form element should be removed");
        assertFalse(result.contains("button"), "button element should be removed");
    }

    @Test
    void testRemovesAllNoiseReturnsEmptyString() {
        String html = """
            <html><body>
            <script>var x=1;</script>
            <style>body{}</style>
            <nav>菜单</nav>
            </body></html>""";

        String result = extractor.clean(html, null);
        // After removing all noise, the body may be empty or contain only whitespace
        assertNotNull(result);
        assertTrue(result.isBlank(), "Result should be blank when all content is noise");
    }

    // ── extractHeaderImage tests (unchanged) ─────────────────

    @Test
    void testExtractHeaderImage() {
        String html = """
            <html><body>
            <img src="https://example.com/header.jpg" alt="头图" />
            <img src="https://example.com/another.jpg" alt="另一张" />
            </body></html>""";

        String headerImage = extractor.extractHeaderImage(html);
        assertEquals("https://example.com/header.jpg", headerImage);
    }

    @Test
    void testExtractHeaderImageSkipsIconsAndPlaceholders() {
        String html = """
            <html><body>
            <img src="https://example.com/icon.png" alt="icon" />
            <img src="https://example.com/avatar.jpg" alt="avatar" />
            <img src="https://example.com/header.jpg" alt="头图" />
            </body></html>""";

        String headerImage = extractor.extractHeaderImage(html);
        assertEquals("https://example.com/header.jpg", headerImage);
    }

    @Test
    void testExtractHeaderImageReturnsNullWhenNoImage() {
        String html = "<html><body><p>纯文本内容</p></body></html>";
        assertNull(extractor.extractHeaderImage(html));
    }

    @Test
    void testExtractHeaderImageHandlesNullInput() {
        assertNull(extractor.extractHeaderImage(null));
        assertNull(extractor.extractHeaderImage(""));
    }
}
