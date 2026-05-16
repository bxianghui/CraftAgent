package com.selfagent.rag;

import com.selfagent.model.ChatRequest;
import com.selfagent.model.ChatResponse;
import com.selfagent.model.LLMProvider;
import java.util.*;

public class QueryDecomposer {
    private final LLMProvider provider;

    public QueryDecomposer(LLMProvider provider) {
        this.provider = provider;
    }

    public List<String> decompose(String query) {
        String prompt = "判断以下问题是否包含多个独立子问题。\n" +
            "如果是，将其拆解为独立子问题列表，每行一个，不超过4个，只输出子问题，不要编号和多余说明。\n" +
            "如果否，原样输出问题本身，不要任何其他内容。\n\n" +
            "问题：" + query;
        try {
            ChatRequest req = new ChatRequest(
                List.of(Map.of("role", "user", "content", prompt)),
                List.of(), null, false, null);
            ChatResponse resp = provider.chat(req);
            if (resp.content == null || resp.content.isBlank()) return List.of(query);
            String[] lines = resp.content.strip().split("\n");
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                String q = line.strip();
                if (!q.isBlank()) result.add(q);
            }
            return result.isEmpty() ? List.of(query) : result;
        } catch (Exception e) {
            System.err.println("[RAG] QueryDecomposer failed, using original query: " + e.getMessage());
            return List.of(query);
        }
    }
}
