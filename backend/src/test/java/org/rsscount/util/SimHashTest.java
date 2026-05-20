package org.rsscount.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class SimHashTest {

    @Test
    @DisplayName("U1-1: 相同文本汉明距离为 0")
    void sameTextHammingDistanceZero() {
        long hash1 = SimHash.compute("北京今日天气晴好适合户外活动");
        long hash2 = SimHash.compute("北京今日天气晴好适合户外活动");
        assertEquals(hash1, hash2, "相同文本的 SimHash 应完全相同");
        assertEquals(0, SimHash.hammingDistance(hash1, hash2));
    }

    @Test
    @DisplayName("U1-2: 相似文本汉明距离 ≤ 3")
    void similarTextHammingDistanceSmall() {
        long hash1 = SimHash.compute("北京今日天气晴好适合户外活动");
        long hash2 = SimHash.compute("北京今天天气晴好适合户外活动");
        int distance = SimHash.hammingDistance(hash1, hash2);
        assertTrue(distance <= 3,
                "相似文本汉明距离应 ≤ 3，实际: " + distance);
    }

    @Test
    @DisplayName("U1-3: 不同文本汉明距离 > 3")
    void differentTextHammingDistanceLarge() {
        long hash1 = SimHash.compute("北京今日天气晴好适合户外活动");
        long hash2 = SimHash.compute("Python 3.13 正式版发布了诸多新的功能特性和性能改进");
        int distance = SimHash.hammingDistance(hash1, hash2);
        assertTrue(distance > 3,
                "不同文本汉明距离应 > 3，实际: " + distance);
    }

    @Test
    @DisplayName("U1-4: 空字符串不抛异常")
    void emptyStringDoesNotThrow() {
        assertDoesNotThrow(() -> SimHash.compute(""));
        assertDoesNotThrow(() -> SimHash.compute(null));
    }

    @Test
    @DisplayName("U1-4: 空字符串返回 0")
    void emptyStringReturnsZero() {
        assertEquals(0L, SimHash.compute(""));
        assertEquals(0L, SimHash.compute(null));
    }

    @Test
    @DisplayName("U1-5: 纯英文正常计算")
    void pureEnglishWorks() {
        long hash = SimHash.compute("Hello World This Is A Test");
        assertNotEquals(0L, hash);
    }

    @Test
    @DisplayName("U1-6: 纯数字/符号正常计算")
    void pureNumbersAndSymbolsWorks() {
        assertDoesNotThrow(() -> {
            SimHash.compute("12345!@#$%");
        }, "Pure numbers and symbols should not throw");
    }

    @Test
    @DisplayName("U1-7: 长文本正常计算，不超时")
    void longTextWorks() {
        StringBuilder sb = new StringBuilder();
        String segment = "人工智能技术正在深刻改变各行各业的运作方式，从医疗保健到金融服务都在经历数字化转型。";
        for (int i = 0; i < 20; i++) {
            sb.append(segment);
        }
        String longText = sb.toString();
        assertTrue(longText.length() > 1000);

        long start = System.currentTimeMillis();
        long hash = SimHash.compute(longText);
        long elapsed = System.currentTimeMillis() - start;

        assertNotEquals(0L, hash);
        assertTrue(elapsed < 5000, "长文本计算不应超过 5 秒，实际: " + elapsed + "ms");
    }

    @Test
    @DisplayName("hammingDistance 对称性")
    void hammingDistanceSymmetric() {
        long a = SimHash.compute("文本A");
        long b = SimHash.compute("文本B");
        assertEquals(SimHash.hammingDistance(a, b), SimHash.hammingDistance(b, a));
    }

    @Test
    @DisplayName("hammingDistance 相同 hash 返回 0")
    void hammingDistanceSameHashIsZero() {
        long hash = 0xABCD1234L;
        assertEquals(0, SimHash.hammingDistance(hash, hash));
    }
}
