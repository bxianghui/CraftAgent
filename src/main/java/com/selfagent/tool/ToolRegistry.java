package com.selfagent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToolRegistry {
    private final Map<String, ToolPlugin> plugins = new LinkedHashMap<>();

    public void register(ToolPlugin plugin) {
        plugins.put(plugin.getDefinition().name, plugin);
    }

    public void unregister(String name) {
        plugins.remove(name);
    }

    public ToolPlugin getPlugin(String name) {
        return plugins.get(name);
    }

    public ToolResult execute(String name, Map<String, Object> params, ExecutionContext ctx) {
        ToolPlugin plugin = plugins.get(name);
        if (plugin == null) return softError("Unknown tool: " + name);
        try {
            ToolResult result = plugin.execute(params, ctx);
            if (result.isError) return softError(result.content);
            return result;
        } catch (Exception e) {
            return softError("Tool error: " + e.getMessage());
        }
    }

    private static ToolResult softError(String message) {
        return ToolResult.ok(message + "\n[Tool failed. Analyze the error and provide the user with a clear explanation and actionable solution. Do not stop the conversation.]");
    }

    public List<ToolDefinition> getDefinitions() {
        return plugins.values().stream()
            .map(ToolPlugin::getDefinition)
            .toList();
    }

    public boolean has(String name) { return plugins.containsKey(name); }

    public void unregisterByServer(String serverName) {
        plugins.entrySet().removeIf(e ->
            e.getValue() instanceof McpToolAdapter a && serverName.equals(a.getServerName()));
    }

    public List<ToolDefinition> getDefinitionsByType(Class<? extends ToolPlugin> type) {
        return plugins.values().stream()
            .filter(type::isInstance)
            .map(ToolPlugin::getDefinition)
            .toList();
    }

    public Map<String, List<ToolDefinition>> getMcpToolsByServer() {
        return plugins.values().stream()
            .filter(p -> p instanceof McpToolAdapter)
            .map(p -> (McpToolAdapter) p)
            .collect(Collectors.groupingBy(
                McpToolAdapter::getServerName,
                LinkedHashMap::new,
                Collectors.mapping(McpToolAdapter::getDefinition, Collectors.toList())
            ));
    }
}
