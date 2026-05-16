package com.selfagent.tool;

import java.util.Map;

public interface ToolPlugin {
    ToolDefinition getDefinition();
    ToolResult execute(Map<String, Object> params, ExecutionContext ctx);
}
