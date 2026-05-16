package com.selfagent.hook;

import java.util.Map;

public record HookInput(
    String event,
    String sessionId,
    String toolName,
    Map<String, Object> toolInput,
    String toolResult,
    boolean isError,
    String userPrompt,
    String source,
    String cwd
) {
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"event\":\"").append(event).append("\"");
        sb.append(",\"session_id\":\"").append(sessionId != null ? sessionId : "").append("\"");
        sb.append(",\"cwd\":\"").append(cwd != null ? cwd.replace("\\", "\\\\") : "").append("\"");
        if (toolName != null) sb.append(",\"tool_name\":\"").append(toolName).append("\"");
        if (toolResult != null) sb.append(",\"tool_result\":\"")
            .append(toolResult.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
        if (userPrompt != null) sb.append(",\"prompt\":\"")
            .append(userPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
        if (source != null) sb.append(",\"source\":\"").append(source).append("\"");
        sb.append(",\"is_error\":").append(isError);
        sb.append("}");
        return sb.toString();
    }
}
