package com.selfagent.agent;

import com.selfagent.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ContextManager {
    private final int maxTokens;
    private final double maxTokenRatio;
    private final int keepRecentTurns;
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Encoding enc;
    private final ObjectMapper mapper = new ObjectMapper();
    private String systemPromptSuffix = "";
    private String customSystemPrompt = null;

    public ContextManager(int maxTokens, double maxTokenRatio, int keepRecentTurns) {
        this.maxTokens = maxTokens;
        this.maxTokenRatio = maxTokenRatio;
        this.keepRecentTurns = keepRecentTurns;
        this.enc = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    }

    public void addUserMessage(String content) {
        history.add(Map.of("role", "user", "content", content));
    }

    /**
     * 添加带 system-reminder block 的 user 消息。
     * content 数组：[{type:text, text:userInput}, {type:text, text:<system-reminder>...</system-reminder>}]
     */
    public void addUserMessageWithReminder(String content, String reminder) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "text", "text", content));
        blocks.add(Map.of("type", "text", "text", "<system-reminder>\n" + reminder + "\n</system-reminder>"));
        history.add(Map.of("role", "user", "content", blocks));
    }

    public void addUserMessageWithReminders(String content, List<String> reminders) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "text", "text", content));
        for (String reminder : reminders) {
            blocks.add(Map.of("type", "text", "text", "<system-reminder>\n" + reminder + "\n</system-reminder>"));
        }
        history.add(Map.of("role", "user", "content", blocks));
    }

    /** 移除最后一条 user 消息（用于 skill 选择轮回退） */
    public void removeLastUserMessage() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).get("role"))) {
                history.remove(i);
                return;
            }
        }
    }

    /** 插入 skill 激活消息对（user prompt + assistant 确认）到 history */
    public void addSkillPair(String skillName, String processedPrompt) {
        history.add(Map.of("role", "user", "content",
            "[SKILL: " + skillName + "]\n" + processedPrompt));
        history.add(Map.of("role", "assistant", "content",
            "好的，我已理解并将按照上述方式工作。"));
    }

    /** 从 history 移除指定 skill 的消息对 */
    public void removeSkillPair(String skillName) {
        String marker = "[SKILL: " + skillName + "]";
        history.removeIf(msg -> {
            Object content = msg.get("content");
            return content instanceof String s && s.startsWith(marker);
        });
        history.removeIf(msg ->
            "assistant".equals(msg.get("role")) &&
            "好的，我已理解并将按照上述方式工作。".equals(msg.get("content")));
    }

    public void addAssistantMessage(String content, List<ToolCall> toolCalls) {
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> blocks = new ArrayList<>();
            if (content != null && !content.isEmpty()) {
                blocks.add(Map.of("type", "text", "text", content));
            }
            for (ToolCall tc : toolCalls) {
                blocks.add(Map.of("type", "tool_use", "id", tc.id, "name", tc.name, "input", tc.arguments));
            }
            history.add(Map.of("role", "assistant", "content", blocks));
        } else if (content != null && !content.isBlank()) {
            history.add(Map.of("role", "assistant", "content", content));
        }
        // 空 content 不写入 history，避免空 assistant 消息破坏后续对话
    }

    public void addToolResult(String toolUseId, String result) {
        history.add(Map.of("role", "user", "content", List.of(
            Map.of("type", "tool_result", "tool_use_id", toolUseId, "content", result)
        )));
    }

    public void setSystemPromptSuffix(String suffix) {
        this.systemPromptSuffix = suffix != null ? suffix : "";
    }

    public List<Map<String, Object>> buildMessages() {
        return Collections.unmodifiableList(history);
    }

    public int estimateTokens() {
        int total = 0;
        for (Map<String, Object> msg : history) {
            Object content = msg.get("content");
            if (content instanceof String s) {
                total += enc.countTokens(s);
            } else if (content instanceof List<?> list) {
                for (Object item : list) {
                    try {
                        total += enc.countTokens(mapper.writeValueAsString(item));
                    } catch (Exception ignored) {}
                }
            }
        }
        return total;
    }

    public boolean needsCompression() {
        return estimateTokens() > maxTokens * maxTokenRatio;
    }

    /**
     * 压缩历史：把早期对话（保留最近 keepRecentTurns 条之外的部分）
     * 通过 LLM 摘要成一条 user 消息，替换到 history 头部。
     * 最近 keepRecentTurns 条消息原样保留，确保上下文连贯。
     */
    public void compress(com.selfagent.model.LLMProvider provider) {
        if (history.size() <= keepRecentTurns) return;

        int splitAt = history.size() - keepRecentTurns;
        List<Map<String, Object>> toSummarize = new ArrayList<>(history.subList(0, splitAt));
        List<Map<String, Object>> toKeep = new ArrayList<>(history.subList(splitAt, history.size()));

        try {
            String compressPrompt = loadCompressPrompt();
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> msg : toSummarize) {
                String role = (String) msg.get("role");
                Object content = msg.get("content");
                if (content instanceof String s) {
                    sb.append(role).append(": ").append(s).append("\n");
                }
            }
            String userMsg = compressPrompt + "\n\n" + sb;
            com.selfagent.model.ChatRequest req = new com.selfagent.model.ChatRequest(
                List.of(Map.of("role", "user", "content", userMsg)),
                List.of(), null, false, null);
            com.selfagent.model.ChatResponse resp = provider.chat(req);
            String summary = resp.content != null ? resp.content : "(summary unavailable)";

            history.clear();
            history.add(Map.of("role", "user",
                "content", "[Earlier conversation summary]\n" + summary));
            history.addAll(toKeep);
            System.out.println("[Context] Compressed " + toSummarize.size()
                + " messages into summary, kept " + toKeep.size() + " recent messages.");
        } catch (Exception e) {
            System.err.println("[Context] Compression failed: " + e.getMessage());
        }
    }


    public void clear() {
        history.clear();
    }

    public void setCustomSystemPrompt(String prompt) {
        this.customSystemPrompt = prompt;
    }

    /**
     * 返回最终 system prompt。
     * 来源优先级：config.yaml system_prompt/system_prompt_file > memory suffix。
     * 两者均为空时返回空字符串，provider 会跳过 system 字段。
     */
    public String buildSystemPrompt() {
        String base = (customSystemPrompt != null && !customSystemPrompt.isBlank())
            ? customSystemPrompt : "";
        if (systemPromptSuffix.isBlank()) return base;
        return base.isBlank() ? systemPromptSuffix : base + "\n" + systemPromptSuffix;
    }

    private static String loadCompressPrompt() {
        try (InputStream is = ContextManager.class.getClassLoader()
                .getResourceAsStream("prompts/compress-history.md")) {
            if (is == null) return "Summarize the following conversation:";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "Summarize the following conversation:";
        }
    }

}
