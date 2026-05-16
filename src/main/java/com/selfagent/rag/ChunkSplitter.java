package com.selfagent.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChunkSplitter {
    private static final int MAX_CHUNK_SIZE = 512;
    private static final int OVERLAP = 50;
    private static final Pattern MD_HEADING = Pattern.compile("(?m)^(#{2,3} .+)");

    public List<String> split(String text, String sourceType) {
        List<String> rawChunks = "md".equals(sourceType)
            ? splitByHeadings(text)
            : splitByParagraphs(text);

        List<String> result = new ArrayList<>();
        for (String chunk : rawChunks) {
            if (chunk.isBlank()) continue;
            if (chunk.length() <= MAX_CHUNK_SIZE) {
                result.add(chunk.trim());
            } else {
                result.addAll(fixedSplit(chunk));
            }
        }
        return result;
    }

    private List<String> splitByHeadings(String text) {
        List<String> chunks = new ArrayList<>();
        Matcher m = MD_HEADING.matcher(text);
        List<int[]> positions = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
            headings.add(m.group(1));
        }
        if (headings.isEmpty()) return splitByParagraphs(text);
        for (int i = 0; i < headings.size(); i++) {
            int bodyStart = positions.get(i)[1];
            int bodyEnd = i + 1 < positions.size() ? positions.get(i + 1)[0] : text.length();
            String body = text.substring(bodyStart, bodyEnd).trim();
            String chunk = headings.get(i) + (body.isEmpty() ? "" : "\n" + body);
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<String> splitByParagraphs(String text) {
        List<String> chunks = new ArrayList<>();
        for (String para : text.split("\n{2,}")) {
            if (!para.isBlank()) chunks.add(para.trim());
        }
        if (chunks.isEmpty()) chunks.add(text.trim());
        return chunks;
    }

    private List<String> fixedSplit(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = end - OVERLAP;
        }
        return chunks;
    }
}
