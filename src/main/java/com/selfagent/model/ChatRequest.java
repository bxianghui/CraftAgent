package com.selfagent.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

public class ChatRequest {
    public final List<Map<String, Object>> messages;
    public final List<ObjectNode> tools;
    public final String model;
    public final boolean stream;
    public final String systemPrompt;

    public ChatRequest(List<Map<String, Object>> messages, List<ObjectNode> tools,
                       String model, boolean stream) {
        this(messages, tools, model, stream, null);
    }

    public ChatRequest(List<Map<String, Object>> messages, List<ObjectNode> tools,
                       String model, boolean stream, String systemPrompt) {
        this.messages = messages;
        this.tools = tools;
        this.model = model;
        this.stream = stream;
        this.systemPrompt = systemPrompt;
    }
}
