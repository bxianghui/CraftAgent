package com.selfagent.memory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 TF-IDF 的轻量语义检索。
 *
 * TF  = 词在查询中的频率（短文本中基本为 1）
 * IDF = log((总文档数 + 1) / (含该词的文档数 + 1))，稀有词权重更高
 *
 * 对每条 description 计算查询词的加权得分，返回得分超过阈值的条目（按分数降序）。
 */
public class TfIdfSearcher {
    private static final Pattern TOKENIZE = Pattern.compile("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF\\u2018-\\u201F]+");
    private static final double SCORE_THRESHOLD = 0.01;

    /**
     * 对 entries 列表按 query 做 TF-IDF 检索，返回相关条目（分数 > 阈值）。
     * 匹配范围：entry.name + " " + entry.description
     */
    public static List<MemoryEntry> search(String query, List<MemoryEntry> entries) {
        if (query == null || query.isBlank() || entries.isEmpty()) return List.of();

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        // 构建每条 entry 的文本语料
        List<List<String>> corpus = entries.stream()
            .map(e -> tokenize((e.name != null ? e.name : "") + " " + (e.description != null ? e.description : "")))
            .collect(Collectors.toList());

        // 计算 IDF：每个查询词在多少条文档中出现
        int docCount = corpus.size();
        Map<String, Double> idf = new HashMap<>();
        for (String token : queryTokens) {
            long df = corpus.stream().filter(doc -> doc.contains(token)).count();
            idf.put(token, Math.log((docCount + 1.0) / (df + 1.0)));
        }

        // 对每条 entry 计算得分
        List<ScoredEntry> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            List<String> doc = corpus.get(i);
            double score = 0;
            for (String token : queryTokens) {
                long tf = doc.stream().filter(t -> t.equals(token)).count();
                score += tf * idf.getOrDefault(token, 0.0);
            }
            if (score > SCORE_THRESHOLD) {
                scored.add(new ScoredEntry(entries.get(i), score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream().map(s -> s.entry).collect(Collectors.toList());
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        // 先按标点/空格切词
        for (String word : TOKENIZE.split(text.toLowerCase())) {
            if (word.isBlank()) continue;
            // 中文字符逐字切分，英文/数字保留整词
            if (word.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
                // 中文：每个汉字单独作为 token，同时保留 2-gram 提高召回
                for (int i = 0; i < word.length(); i++) {
                    tokens.add(String.valueOf(word.charAt(i)));
                    if (i + 1 < word.length()) tokens.add(word.substring(i, i + 2));
                }
            } else if (word.length() > 1) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private record ScoredEntry(MemoryEntry entry, double score) {}
}
