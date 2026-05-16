package com.selfagent.agent.multi;

public record SubAgentTask(
    String subagentType,
    String prompt,
    String description,
    String autoSystemPrompt,
    boolean runInBackground
) {}
