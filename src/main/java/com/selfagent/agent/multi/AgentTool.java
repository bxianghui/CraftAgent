package com.selfagent.agent.multi;

import com.selfagent.agent.AgentContext;
import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AgentTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final AgentContext agentCtx;

    public AgentTool(AgentContext agentCtx) {
        this.agentCtx = agentCtx;
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("description").put("type", "string")
            .put("description", "Short description of what this agent will do (shown in terminal)");
        props.putObject("prompt").put("type", "string")
            .put("description", "The task for the agent to perform");
        props.putObject("subagent_type").put("type", "string")
            .put("description", "Agent type to use. 'auto' = temporary agent with custom system prompt. Defaults to general-purpose.");
        props.putObject("run_in_background").put("type", "boolean")
            .put("description", "Set to true to run this agent in the background for parallel execution with other agents");
        props.putObject("auto_system_prompt").put("type", "string")
            .put("description", "System prompt for the agent when subagent_type is 'auto'");
        schema.putArray("required").add("prompt");

        StringBuilder desc = new StringBuilder(
            "Launch a new agent to handle tasks. Spawning agents is ALWAYS preferred over doing work directly.\n\n" +
            "## MANDATORY rules (no exceptions)\n" +
            "- Reading or analyzing 3+ files → MUST spawn parallel agents, NEVER call read_file directly\n" +
            "- Exploring a directory with multiple concerns → spawn one explore agent per concern\n" +
            "- 2+ independent subtasks → spawn one agent per task with run_in_background=true\n" +
            "- Any search + read combination → spawn an explore agent\n\n" +
            "## Only skip Agent when ALL of these are true\n" +
            "- Single lookup involving exactly 1 file or 1 command\n" +
            "- Each step strictly depends on the previous result (cannot parallelize)\n\n" +
            "## Parallel execution\n" +
            "When tasks are independent, call this tool multiple times in a single response with run_in_background=true. " +
            "Do NOT wait — launch all agents first, then continue.\n\n" +
            "## Available agent types and the tools they have access to:\n");
        if (agentCtx.agentRegistry != null) {
            agentCtx.agentRegistry.listAll().forEach(d -> desc.append(d.toListingLine()).append("\n"));
        }
        desc.append("\nIMPORTANT: Before using this tool, check if there is already a running or recently completed agent that you can reuse.\n");
        desc.append("When calling, specify a subagent_type. If omitted, the general-purpose agent is used.\n");
        desc.append("When you launch multiple agents for independent work, send them in a single message with run_in_background=true so they run concurrently.");

        return new ToolDefinition("Agent", desc.toString(), schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ToolResult.ok("prompt parameter is required for Agent tool.");
        }
        if (agentCtx.orchestrator == null) {
            return ToolResult.ok("Agent orchestrator not initialized. Multi-agent support is unavailable in this mode.\n" +
                "[Inform the user and answer the request directly without using sub-agents.]");
        }
        String subagentType = params.containsKey("subagent_type")
            ? params.get("subagent_type").toString() : "general-purpose";
        String description = params.containsKey("description")
            ? params.get("description").toString() : prompt;
        String autoPrompt = params.containsKey("auto_system_prompt")
            ? params.get("auto_system_prompt").toString() : null;
        boolean background = Boolean.TRUE.equals(params.get("run_in_background"));

        SubAgentTask task = new SubAgentTask(subagentType, prompt, description, autoPrompt, background);

        if (background) {
            CompletableFuture<SubAgentResult> future = agentCtx.orchestrator.submit(task, agentCtx);
            agentCtx.backgroundAgentFutures.add(future);
            // 告知模型 outputFile 路径，可用 bash tail 实时查看进度
            return ToolResult.ok("Background agent launched.\n" +
                "- subagent_type: " + subagentType + "\n" +
                "- description: " + description + "\n" +
                "- You can check progress with: bash tail -f <outputFile>\n" +
                "- Result will be delivered as <task-notification> in the next turn.\n" +
                "[Continue with other tasks. Do NOT wait for this agent.]");
        } else {
            SubAgentResult result = agentCtx.orchestrator.run(task, agentCtx);
            return ToolResult.ok(result.toToolResult());
        }
    }
}
