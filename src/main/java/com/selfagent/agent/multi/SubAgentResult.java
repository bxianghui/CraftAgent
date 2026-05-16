package com.selfagent.agent.multi;

public record SubAgentResult(
    String agentId,
    String subagentType,
    String description,
    String result,
    String outputFile,
    boolean success,
    long durationMs
) {
    public String toToolResult() {
        return "[Agent: " + subagentType + " | " + description + " | " + durationMs + "ms]\n" + result;
    }

    public String toTaskNotification() {
        return "<task-notification>\n" +
            "<task-id>" + agentId + "</task-id>\n" +
            "<status>" + (success ? "completed" : "failed") + "</status>\n" +
            "<agent-type>" + subagentType + "</agent-type>\n" +
            "<description>" + description + "</description>\n" +
            (outputFile != null ? "<output_file>" + outputFile + "</output_file>\n" : "") +
            "<result>\n" + result + "\n</result>\n" +
            "</task-notification>";
    }
}
