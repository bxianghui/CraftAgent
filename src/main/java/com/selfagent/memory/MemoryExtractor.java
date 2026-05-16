package com.selfagent.memory;

import com.selfagent.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 调用 LLM 从对话历史中提炼并写入长期记忆。
 *
 * session 结束时，promoteFromHistory() 将完整对话历史 + 现有记忆发给 LLM，
 * 由 LLM 一次性决定：新增哪些条目、合并哪些同主题条目、删除哪些过时条目。
 */
public class MemoryExtractor {
    private final LLMProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String promotePrompt;

    public MemoryExtractor(LLMProvider provider) {
        this.provider = provider;
        this.promotePrompt = loadPrompt("prompts/promote-memory.md");
    }

    /**
     * Session 结束时调用，从完整对话历史 + 现有记忆，由 LLM 一次性决定最终记忆列表。
     * LLM 的输出即最终结果：包含的条目写入，不包含的旧条目删除。
     */
    public void promoteFromHistory(List<Map<String, Object>> history, PersistentMemory persistent) {
        if (history.isEmpty()) return;
        try {
            String conversationText = formatMessages(history);
            if (conversationText.isBlank()) return;

            List<MemoryEntry> existing = persistent.loadIndex();
            StringBuilder existingText = new StringBuilder();
            for (MemoryEntry e : existing) {
                existingText.append("- [").append(e.type != null ? e.type : "project").append("] ")
                    .append(e.name).append(": ").append(e.description).append("\n");
            }

            String userMsg = promotePrompt
                + "\n\n本次完整对话：\n" + conversationText
                + (existingText.isEmpty() ? "" : "\n\n已有长期记忆：\n" + existingText);

            ChatRequest req = new ChatRequest(
                List.of(Map.of("role", "user", "content", userMsg)),
                List.of(), null, false, null);
            ChatResponse resp = provider.chat(req);
            if (resp.content == null || resp.content.isBlank()) return;

            List<Map<String, String>> raw = mapper.readValue(extractJson(resp.content), new TypeReference<>() {});

            for (MemoryEntry e : existing) {
                persistent.delete(e.name);
            }
            for (Map<String, String> m : raw) {
                persistent.save(new MemoryEntry(
                    m.getOrDefault("name", "unknown"),
                    m.getOrDefault("description", ""),
                    m.getOrDefault("type", "project"),
                    m.getOrDefault("content", "")));
            }
            System.out.println("[Memory] Session end: " + raw.size() + " entries saved (was " + existing.size() + ")");
        } catch (Exception e) {
            System.err.println("[Memory] Promote from history failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String formatMessages(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            Object content = msg.get("content");

            if ("user".equals(role) && content instanceof String s && !s.isBlank()) {
                sb.append("user: ").append(s).append("\n");
            } else if ("assistant".equals(role) && content instanceof List<?> blocks) {
                for (Object block : blocks) {
                    if (block instanceof Map<?, ?> m) {
                        String type = (String) ((Map<String, Object>) m).get("type");
                        if ("text".equals(type)) {
                            Object text = ((Map<String, Object>) m).get("text");
                            if (text instanceof String ts && !ts.isBlank()) {
                                sb.append("assistant: ").append(ts).append("\n");
                            }
                        }
                    }
                }
            } else if ("assistant".equals(role) && content instanceof String s && !s.isBlank()) {
                sb.append("assistant: ").append(s).append("\n");
            }
        }
        return sb.toString();
    }

    /** 从 LLM 输出中提取 JSON 数组，容忍前后多余文字。 */
    private String extractJson(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return "[]";
    }

    private String loadPrompt(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
