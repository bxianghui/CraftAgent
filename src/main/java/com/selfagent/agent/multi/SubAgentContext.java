package com.selfagent.agent.multi;

import com.selfagent.agent.AgentContext;
import com.selfagent.agent.ContextManager;
import com.selfagent.model.LLMProvider;
import com.selfagent.tool.ToolPlugin;
import com.selfagent.tool.ToolRegistry;
import java.util.List;
import java.util.Map;

public class SubAgentContext {
    public final AgentContext parentCtx;
    public final AgentDefinition definition;
    public final ContextManager contextManager;
    public final ToolRegistry toolRegistry;
    public final LLMProvider provider;
    public final boolean isSubAgent = true;

    public SubAgentContext(AgentContext parentCtx, AgentDefinition definition,
                           List<Map<String, Object>> parentHistoryPrefix) {
        this.parentCtx = parentCtx;
        this.definition = definition;
        this.provider = parentCtx.provider;

        this.contextManager = new ContextManager(provider.maxTokens(), 0.8, 20);
        if (definition.systemPrompt != null && !definition.systemPrompt.isBlank()) {
            this.contextManager.setCustomSystemPrompt(definition.systemPrompt);
        }
        // 注入父 history 前缀（最近 10 条）
        int prefixSize = Math.min(parentHistoryPrefix.size(), 10);
        // 创建副本防止并发 ConcurrentModificationException
        List<Map<String, Object>> prefix = new java.util.ArrayList<>(parentHistoryPrefix.subList(
            Math.max(0, parentHistoryPrefix.size() - prefixSize), parentHistoryPrefix.size()));
        for (Map<String, Object> msg : prefix) {
            String role = (String) msg.get("role");
            Object content = msg.get("content");
            if ("user".equals(role) && content instanceof String s) {
                contextManager.addUserMessage(s);
            } else if ("assistant".equals(role) && content instanceof String s) {
                contextManager.addAssistantMessage(s, List.of());
            }
        }

        this.toolRegistry = buildToolRegistry(parentCtx, definition);
    }

    private static ToolRegistry buildToolRegistry(AgentContext parentCtx, AgentDefinition def) {
        ToolRegistry filtered = new ToolRegistry();
        if (parentCtx.toolRegistry == null) return filtered;
        boolean canSpawnAgents = parentCtx.orchestrator != null;
        parentCtx.toolRegistry.getDefinitions().forEach(td -> {
            // 只有当深度未超限（orchestrator 非 null）时才注册 Agent tool
            if ("Agent".equals(td.name) && !canSpawnAgents) return;
            if (def.allowedTools == null || def.allowedTools.contains(td.name)) {
                ToolPlugin plugin = parentCtx.toolRegistry.getPlugin(td.name);
                if (plugin != null) filtered.register(plugin);
            }
        });
        return filtered;
    }
}
