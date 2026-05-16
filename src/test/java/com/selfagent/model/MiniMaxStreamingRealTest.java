package com.selfagent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 使用真实 MiniMax API 验证流式 tool call 参数拼接是否正确。
 * 运行前确保 config.yaml 中 minimax api_key 已配置。
 */
class MiniMaxStreamingRealTest {

    @Test
    void streamingToolCallWithRealApi() {
        ObjectMapper mapper = new ObjectMapper();

        // 构建 bash tool schema
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("cmd").put("type", "string").put("description", "Shell command");
        schema.putArray("required").add("cmd");
        ObjectNode toolDef = mapper.createObjectNode();
        toolDef.put("name", "bash");
        toolDef.put("description", "Execute a shell command");
        toolDef.set("input_schema", schema);

        MiniMaxProvider provider = new MiniMaxProvider(
            "sk-cp-QCfYmDJI3wiGcaGINzXoQQY39O5-Snn1Ke24YptmRqasLppGJHxFaF5AVX-E02iiJ3e7wwNdOCFeUiTxwi1hqb9n5QLZh9tuEVQLN6YvWASBjG4hEF1zveI",
            "MiniMax-M2.7",
            "https://api.minimaxi.com/anthropic",
            0.0f
        );

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "用 bash 执行 echo hello")),
            List.of(toolDef), "MiniMax-M2.7", true);

        System.out.println("=== Streaming chunks ===");
        List<ChatChunk> chunks = provider.stream(req).collect(Collectors.toList());

        for (ChatChunk chunk : chunks) {
            if (chunk.done) {
                System.out.println("[DONE]");
            } else if (chunk.deltaContent != null) {
                System.out.println("[TEXT] " + chunk.deltaContent);
            } else if (chunk.thinkingDelta != null) {
                System.out.println("[THINK] " + chunk.thinkingDelta);
            } else if (chunk.toolCallId != null && chunk.toolCallName != null) {
                System.out.println("[TOOL_START] id=" + chunk.toolCallId + " name=" + chunk.toolCallName);
            } else if (chunk.toolCallArgsDelta != null) {
                System.out.println("[TOOL_ARGS] id=" + chunk.toolCallId + " delta=" + chunk.toolCallArgsDelta);
            }
        }

        System.out.println("\n=== Reconstructed tool calls ===");
        // 模拟 ReactLoop.collectStream 的拼接逻辑
        java.util.Map<String, StringBuilder> toolArgsMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> toolNameMap = new java.util.LinkedHashMap<>();
        for (ChatChunk chunk : chunks) {
            if (chunk.toolCallId != null && chunk.toolCallName != null) {
                toolNameMap.put(chunk.toolCallId, chunk.toolCallName);
                toolArgsMap.putIfAbsent(chunk.toolCallId, new StringBuilder());
            }
            if (chunk.toolCallArgsDelta != null && chunk.toolCallId != null) {
                toolArgsMap.computeIfAbsent(chunk.toolCallId, k -> new StringBuilder())
                    .append(chunk.toolCallArgsDelta);
            }
        }
        toolNameMap.forEach((id, name) -> {
            String args = toolArgsMap.getOrDefault(id, new StringBuilder()).toString();
            System.out.println("Tool: " + name + " id=" + id);
            System.out.println("Args JSON: " + args);
            try {
                Map<?, ?> parsed = new ObjectMapper().readValue(args, Map.class);
                System.out.println("Parsed: " + parsed);
                System.out.println("cmd param: " + parsed.get("cmd"));
            } catch (Exception e) {
                System.out.println("Parse error: " + e.getMessage());
            }
        });
    }
}
