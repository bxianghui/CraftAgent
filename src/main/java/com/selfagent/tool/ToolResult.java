package com.selfagent.tool;

public class ToolResult {
    public final String content;
    public final boolean isError;

    private ToolResult(String content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    public static ToolResult ok(String content) { return new ToolResult(content, false); }
    public static ToolResult error(String message) { return new ToolResult(message, true); }
}
