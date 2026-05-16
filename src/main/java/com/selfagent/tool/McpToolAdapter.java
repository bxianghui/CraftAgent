package com.selfagent.tool;

import java.io.IOException;
import java.util.Map;

public class McpToolAdapter implements ToolPlugin {
    private final McpClient client;
    private final ToolDefinition definition;

    public McpToolAdapter(McpClient client, ToolDefinition definition) {
        this.client = client;
        this.definition = definition;
    }

    public String getServerName() { return client.getServerName(); }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        try {
            return client.callTool(definition.name, params);
        } catch (IOException e) {
            return ToolResult.error("MCP call failed: " + e.getMessage());
        }
    }
}
