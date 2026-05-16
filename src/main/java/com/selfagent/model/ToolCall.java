package com.selfagent.model;

import java.util.Map;

public class ToolCall {
    public final String id;
    public final String name;
    public final Map<String, Object> arguments;

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }
}
