package com.selfagent.model;

import java.util.List;

public class ChatResponse {
    public final String content;
    public final String thinkingContent;
    public final List<ToolCall> toolCalls;
    public final int inputTokens;
    public final int outputTokens;

    public ChatResponse(String content, String thinkingContent, List<ToolCall> toolCalls,
                        int inputTokens, int outputTokens) {
        this.content = content;
        this.thinkingContent = thinkingContent;
        this.toolCalls = toolCalls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
