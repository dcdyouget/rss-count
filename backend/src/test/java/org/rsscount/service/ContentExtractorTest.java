package org.rsscount.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * U4: ContentExtractor tests.
 *
 * U4-1: Extract headings correctly.
 * U4-2: Extract paragraphs correctly.
 * U4-3: Extract images correctly.
 * U4-4: Extract blockquotes correctly.
 * U4-5: Extract lists correctly.
 * U4-6: Extract code blocks correctly.
 * U4-7: Noise elements (script, style, nav, etc.) are removed.
 */
@QuarkusTest
class ContentExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    ContentExtractor extractor;

    // ── Helpers ─────────────────────────────────────────────

    private List<JsonNode> parseBlocks(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            List<JsonNode> result = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(result::add);
            }
            return result;
        } catch (JsonProcessingException e) {
            fail("JSON parse error: " + e.getMessage());
            return List.of();
        }
    }

    // ── U4-1: Extract headings ──────────────────────────────

    @Test
    void testExtractHeadings() {
        String html = """
            <html><body>
            <h1>主标题</h1>
            <h2>二级标题</h2>
            <h3>三级标题</h3>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(3, blocks.size());
        assertEquals("heading", blocks.get(0).get("type").asText());
        assertEquals("主标题", blocks.get(0).get("text").asText());
        assertEquals(1, blocks.get(0).get("level").asInt());

        assertEquals("heading", blocks.get(1).get("type").asText());
        assertEquals("二级标题", blocks.get(1).get("text").asText());
        assertEquals(2, blocks.get(1).get("level").asInt());

        assertEquals("heading", blocks.get(2).get("type").asText());
        assertEquals("三级标题", blocks.get(2).get("text").asText());
        assertEquals(3, blocks.get(2).get("level").asInt());
    }

    @Test
    void testExtractHeadingsEmptyText() {
        String html = "<html><body><h1>  </h1></body></html>";
        String json = extractor.extract(html);
        assertEquals("[]", json);
    }

    // ── U4-2: Extract paragraphs ────────────────────────────

    @Test
    void testExtractParagraphs() {
        String html = """
            <html><body>
            <p>这是第一段文字。</p>
            <p>这是第二段文字，包含一些内容。</p>
            <p>  带空白的段落  </p>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(3, blocks.size());
        assertEquals("paragraph", blocks.get(0).get("type").asText());
        assertEquals("这是第一段文字。", blocks.get(0).get("text").asText());

        assertEquals("这是第二段文字，包含一些内容。", blocks.get(1).get("text").asText());
    }

    @Test
    void testExtractParagraphsWithInlineElements() {
        String html = """
            <html><body>
            <p><strong>加粗</strong>和<em>斜体</em>文字</p>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(1, blocks.size());
        assertEquals("paragraph", blocks.get(0).get("type").asText());
        // Inline elements: strong text is in children
        assertTrue(blocks.get(0).get("text").asText().contains("加粗"));
    }

    // ── U4-3: Extract images ────────────────────────────────

    @Test
    void testExtractImages() {
        String html = """
            <html><body>
            <img src="https://example.com/img1.jpg" alt="图片1" />
            <img src="https://example.com/img2.png" alt="图片2" />
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(2, blocks.size());
        assertEquals("image", blocks.get(0).get("type").asText());
        assertEquals("https://example.com/img1.jpg", blocks.get(0).get("src").asText());
        assertEquals("图片1", blocks.get(0).get("alt").asText());

        assertEquals("https://example.com/img2.png", blocks.get(1).get("src").asText());
    }

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

    // ── U4-4: Extract blockquotes ───────────────────────────

    @Test
    void testExtractBlockquotes() {
        String html = """
            <html><body>
            <blockquote>这是一段引用文字。</blockquote>
            <p>正常段落</p>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(2, blocks.size());
        assertEquals("blockquote", blocks.get(0).get("type").asText());
        assertEquals("这是一段引用文字。", blocks.get(0).get("text").asText());
        assertEquals("paragraph", blocks.get(1).get("type").asText());
    }

    // ── U4-5: Extract lists ─────────────────────────────────

    @Test
    void testExtractUnorderedList() {
        String html = """
            <html><body>
            <ul>
            <li>列表项一</li>
            <li>列表项二</li>
            <li>列表项三</li>
            </ul>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(1, blocks.size());
        assertEquals("list", blocks.get(0).get("type").asText());
        assertTrue(blocks.get(0).has("items"));
        assertEquals(3, blocks.get(0).get("items").size());
        assertEquals("列表项一", blocks.get(0).get("items").get(0).asText());
        assertEquals("列表项二", blocks.get(0).get("items").get(1).asText());
        assertEquals("列表项三", blocks.get(0).get("items").get(2).asText());
    }

    @Test
    void testExtractOrderedList() {
        String html = """
            <html><body>
            <ol>
            <li>第一步</li>
            <li>第二步</li>
            </ol>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(1, blocks.size());
        assertEquals("list", blocks.get(0).get("type").asText());
        assertEquals(2, blocks.get(0).get("items").size());
    }

    // ── U4-6: Extract code blocks ───────────────────────────

    @Test
    void testExtractCodeBlock() {
        String html = """
            <html><body>
            <pre><code>public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }</code></pre>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(1, blocks.size());
        assertEquals("code", blocks.get(0).get("type").asText());
        assertTrue(blocks.get(0).get("text").asText().contains("public class Hello"));
        assertTrue(blocks.get(0).get("text").asText().contains("System.out.println"));
    }

    // ── U4-7: Noise elements are removed ────────────────────

    @Test
    void testNoiseElementsRemoved() {
        String html = """
            <html><body>
            <script>alert('bad');</script>
            <style>.css{color:red}</style>
            <nav>导航栏</nav>
            <div class="ad">广告内容</div>
            <div class="sidebar">侧边栏</div>
            <footer>页脚</footer>
            <p>正文内容</p>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        // Only the <p> should remain
        assertEquals(1, blocks.size());
        assertEquals("paragraph", blocks.get(0).get("type").asText());
        assertEquals("正文内容", blocks.get(0).get("text").asText());
    }

    @Test
    void testAllNoiseRemovedReturnsEmptyArray() {
        String html = """
            <html><body>
            <script>var x=1;</script>
            <style>body{}</style>
            <nav>菜单</nav>
            </body></html>""";

        String json = extractor.extract(html);
        assertEquals("[]", json);
    }

    @Test
    void testNullAndEmptyHtmlReturnsNull() {
        assertNull(extractor.extract(null));
        assertNull(extractor.extract(""));
        assertNull(extractor.extract("   "));
    }

    @Test
    void testMixedContentPreservesOrder() {
        String html = """
            <html><body>
            <h1>文章标题</h1>
            <p>导语段落</p>
            <img src="pic.jpg" alt="插图" />
            <blockquote>重要引用</blockquote>
            <p>后续段落</p>
            <ul>
            <li>总结一</li>
            <li>总结二</li>
            </ul>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        assertEquals(6, blocks.size());
        assertEquals("heading", blocks.get(0).get("type").asText());
        assertEquals("paragraph", blocks.get(1).get("type").asText());
        assertEquals("image", blocks.get(2).get("type").asText());
        assertEquals("blockquote", blocks.get(3).get("type").asText());
        assertEquals("paragraph", blocks.get(4).get("type").asText());
        assertEquals("list", blocks.get(5).get("type").asText());
    }

    @Test
    void testExtractHandlesNestedDivAndSection() {
        String html = """
            <html><body>
            <article>
                <section>
                    <h2>章节标题</h2>
                    <p>章节内容</p>
                </section>
                <div>
                    <p>更多内容</p>
                    <img src="nested.jpg" alt="嵌套图片" />
                </div>
            </article>
            </body></html>""";

        String json = extractor.extract(html);
        List<JsonNode> blocks = parseBlocks(json);

        // h2, p, p, img
        assertEquals(4, blocks.size());
        assertEquals("heading", blocks.get(0).get("type").asText());
        assertEquals("章节标题", blocks.get(0).get("text").asText());
        assertEquals("image", blocks.get(3).get("type").asText());
    }
}
