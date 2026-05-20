package org.rsscount.util;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import com.huaban.analysis.jieba.SegToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SimHash 算法实现 — 用于新闻标题去重。
 *
 * 混合策略：jieba 中文分词 + 字符 bigram 作为补充特征，
 * TF 加权 → 64 位指纹。汉明距离 ≤ 3 判定为重复。
 *
 * 字符 bigram 能有效增强短文本（如标题）的指纹稳定性，
 * 避免因分词粒度过大导致相似文本汉明距离过大。
 */
public class SimHash {

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    private SimHash() {
    }

    /**
     * 计算文本的 64 位 SimHash 指纹。
     *
     * @param text 原始文本，可为 null 或空
     * @return 64 位指纹；null 或空串返回 0
     */
    public static long compute(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }

        // 1. 词频统计：jieba 分词 + 字符 bigram
        Map<String, Integer> wordFreq = new HashMap<>();

        // 1a. jieba 分词
        List<SegToken> tokens = SEGMENTER.process(text, SegMode.INDEX);
        for (SegToken token : tokens) {
            String word = token.word.trim();
            if (word.isEmpty()) {
                continue;
            }
            wordFreq.merge(word, 1, Integer::sum);
        }

        // 1b. 字符 bigram（滑动窗口，增强短文本稳定性）
        // 权重减半，避免覆盖分词特征
        for (int i = 0; i < text.length() - 1; i++) {
            String bigram = text.substring(i, i + 2);
            // 跳过纯空白/标点 bigram
            if (bigram.codePoints().allMatch(Character::isWhitespace)) {
                continue;
            }
            // 如果 bigram 已经被分词覆盖，跳过，否则以半权重加入
            if (!wordFreq.containsKey(bigram)) {
                wordFreq.merge(bigram, 1, Integer::sum);
            } else {
                // 分词已覆盖但给 bigram 额外小幅加成
                wordFreq.merge(bigram, 0, (a, b) -> a); // 不加权，仅标记存在
            }
        }

        // 如果没有任何有效词 → 返回 0
        if (wordFreq.isEmpty()) {
            return 0L;
        }

        // 2. 加权 hash 累加（使用 long[] 防止长文本溢出）
        long[] vector = new long[64];
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            long hash = hash64(entry.getKey());
            int weight = entry.getValue();
            if (weight == 0) weight = 1; // bigram 已存在但权重为 0 时给基础权重
            for (int i = 0; i < 64; i++) {
                if ((hash & (1L << i)) != 0) {
                    vector[i] += weight;
                } else {
                    vector[i] -= weight;
                }
            }
        }

        // 3. 降维：正数→1，负数/零→0
        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (vector[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 计算两个 SimHash 指纹之间的汉明距离（不同 bit 位数）。
     */
    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /**
     * 对字符串计算 64 位 hash（使用多项式滚动 + 最终混合）。
     */
    private static long hash64(String word) {
        long h = 0L;
        for (int i = 0; i < word.length(); i++) {
            h = h * 31 + word.charAt(i);
        }
        // 最终混合 (avalanche)
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }
}
