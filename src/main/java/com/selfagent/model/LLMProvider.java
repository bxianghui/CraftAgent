package com.selfagent.model;

import java.util.stream.Stream;

public interface LLMProvider {
    ChatResponse chat(ChatRequest request);
    Stream<ChatChunk> stream(ChatRequest request);
    boolean supportsNativeToolUse();
    int maxTokens();
}
