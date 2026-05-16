package com.selfagent.rag;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.stream.Collectors;

public class RagSearchTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int TOP_K = 5;

    private final RagStore store;
    private final EmbeddingService embedding;
    private final QueryDecomposer decomposer;

    public RagSearchTool(RagStore store, EmbeddingService embedding, QueryDecomposer decomposer) {
        this.store = store;
        this.embedding = embedding;
        this.decomposer = decomposer;
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("query").put("type", "string")
            .put("description", "The question or keywords to search for in internal documents");
        schema.putArray("required").add("query");
        return new ToolDefinition("search_docs",
            "Search internal documents and knowledge base. Use this tool when:\n" +
            "- The question involves internal/private knowledge, internal concepts, or internal docs\n" +
            "- User mentions '内部', '内网', '我们的', '文档里', '内部系统' etc.\n" +
            "- The answer is likely in imported documents rather than general knowledge\n" +
            "- You are uncertain and the topic seems domain-specific or proprietary",
            schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        if (store.isEmpty()) {
            return ToolResult.ok("No documents imported yet. Use /import <path|url> to add documents.");
        }
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.ok("query parameter is required.");
        }
        try {
            List<String> subQueries = decomposer.decompose(query);
            Map<String, RagChunk> seen = new LinkedHashMap<>();
            for (String sq : subQueries) {
                float[] vec = embedding.embed(sq);
                List<RagChunk> results = store.search(vec, TOP_K);
                for (RagChunk c : results) {
                    String key = c.docId() + "#" + c.chunkIndex();
                    seen.putIfAbsent(key, c);
                }
            }
            List<RagChunk> merged = seen.values().stream()
                .sorted(Comparator.comparingDouble(RagChunk::score))
                .limit(TOP_K)
                .collect(Collectors.toList());
            if (merged.isEmpty()) return ToolResult.ok("No relevant documents found.");
            StringBuilder sb = new StringBuilder();
            for (RagChunk c : merged) {
                sb.append("[来源: ").append(c.source()).append("]\n");
                sb.append(c.text()).append("\n\n");
            }
            return ToolResult.ok(sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.ok("Search failed: " + e.getMessage() +
                "\n[Analyze the error and suggest a solution to the user.]");
        }
    }
}
