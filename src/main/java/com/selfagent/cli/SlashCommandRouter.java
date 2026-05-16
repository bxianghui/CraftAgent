package com.selfagent.cli;

import com.selfagent.agent.AgentContext;
import com.selfagent.common.ThinkingLogger;
import com.selfagent.common.TimingLogger;
import com.selfagent.agent.ContextManager;
import com.selfagent.history.HistoryEvent;
import com.selfagent.history.HistoryRenderer;
import com.selfagent.history.HistoryStore;
import com.selfagent.memory.MemoryEntry;
import com.selfagent.model.AgentConfig;
import com.selfagent.model.LLMProvider;
import com.selfagent.rag.*;
import com.selfagent.skill.SkillDefinition;
import com.selfagent.skill.SkillManager;
import com.selfagent.tool.McpConfigEditor;
import com.selfagent.tool.McpManager;
import com.selfagent.tool.McpToolAdapter;
import com.selfagent.tool.ToolDefinition;
import com.selfagent.tool.ToolRegistry;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SlashCommandRouter {
    private final ToolRegistry registry;
    private final ContextManager contextManager;
    private final McpManager mcpManager;
    private final HistoryStore historyStore;
    private final Function<String, LLMProvider> providerFactory;
    private final SkillManager skillManager;
    private final PrintStream out;

    public SlashCommandRouter(ToolRegistry registry, ContextManager contextManager,
                              McpManager mcpManager, HistoryStore historyStore,
                              Function<String, LLMProvider> providerFactory,
                              SkillManager skillManager, PrintStream out) {
        this.registry = registry;
        this.contextManager = contextManager;
        this.mcpManager = mcpManager;
        this.historyStore = historyStore;
        this.providerFactory = providerFactory;
        this.skillManager = skillManager;
        this.out = out;
    }

    public static boolean isSlashCommand(String input) {
        return input != null && input.startsWith("/") && input.length() > 1;
    }

    public boolean handle(String input, AgentContext ctx) {
        String[] parts = input.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : null;

        // 动态 /<skill-name> 路由
        String skillName = cmd.substring(1);
        SkillManager sm = (ctx != null) ? ctx.skillManager : skillManager;
        if (sm != null && sm.hasSkill(skillName)) {
            sm.activate(skillName, ctx != null ? ctx.workingDir : null);
            return true;
        }

        switch (cmd) {
            case "/help" -> printHelp();
            case "/clear" -> {
                contextManager.clear();
                out.println("Context cleared.");
            }
            case "/tools" -> listTools();
            case "/mcp" -> handleMcp(arg);
            case "/history" -> handleHistory(arg);
            case "/model" -> handleModel(arg, ctx);
            case "/models" -> handleModels(ctx);
            case "/sessions" -> handleSessions();
            case "/skill" -> handleSkillCommand(arg, ctx);
            case "/skills" -> handleSkills(ctx);
            case "/import" -> handleImport(arg, ctx);
            case "/rag" -> handleRag(arg, ctx);
            case "/timing" -> handleTiming(arg);
            case "/thinking" -> handleThinking(arg);
            case "/sandbox" -> handleSandbox(arg, ctx);
            case "/agent" -> handleAgent(arg, ctx);
            case "/verify" -> handleVerify(arg, ctx);
            case "/memory" -> handleMemory(arg, ctx);
            case "/remember" -> handleRemember(arg, ctx);
            case "/forget" -> handleForget(arg, ctx);
            default -> {
                out.println("Unknown command: " + cmd + ". Type /help for available commands.");
                return false;
            }
        }
        return true;
    }

    private void printHelp() {
        out.println("Available commands:");
        out.println("  /model <name>                      Switch LLM provider at runtime");
        out.println("  /models                            List all configured providers");
        out.println("  /clear                             Clear conversation context");
        out.println("  /tools                             List all available tools");
        out.println("  /history                           List recent sessions");
        out.println("  /history <sessionId|last>          Show session call chain");
        out.println("  /mcp                               List connected MCP servers");
        out.println("  /mcp <name>                        Show tools of a server");
        out.println("  /mcp enable|disable <name>         Toggle MCP server");
        out.println("  /mcp refresh                       Reload config and connect new servers");
        out.println("  /skills                            List all loaded skills");
        out.println("  /skill <name>                      Use a skill");
        out.println("  /skill refresh                     Reload skills from disk");
        out.println("  /<skill-name>                      Directly activate a skill");
        out.println("  /import <path|url>                 Import document into RAG knowledge base");
        out.println("  /rag                               Show RAG status and imported docs");
        out.println("  /rag on|off                        Enable or disable RAG globally");
        out.println("  /timing                            Show timing log status");
        out.println("  /timing on|off                     Enable or disable timing logs (persisted)");
        out.println("  /thinking                          Show thinking log status");
        out.println("  /thinking on|off                   Enable or disable thinking display (persisted)");
        out.println("  /sandbox                           Show sandbox status and config");
        out.println("  /sandbox on|off                    Enable or disable sandbox");
        out.println("  /agent <task>                      Launch a general-purpose agent for a task");
        out.println("  /agent <type> <task>               Launch a specific agent type");
        out.println("  /verify                            Run verification agent on recent changes");
        out.println("  /verify <prompt>                   Run verification with custom prompt");
        out.println("  /memory                            Show memory (phase 7)");
        out.println("  /remember <txt>                    Save a memory (phase 7)");
        out.println("  /forget <kw>                       Delete a memory (phase 7)");
        out.println("  /help                              Show this help");
    }

    private void listTools() {
        List<ToolDefinition> defs = registry.getDefinitions();
        if (defs.isEmpty()) {
            out.println("No tools registered.");
            return;
        }
        out.println("\033[1mTools (" + defs.size() + ")\033[0m");
        out.println();
        defs.forEach(d -> {
            // 截取第一句话作为简短描述
            String shortDesc = d.description.contains(".")
                ? d.description.substring(0, d.description.indexOf('.') + 1)
                : (d.description.length() > 80 ? d.description.substring(0, 80) + "..." : d.description);
            out.println("  \033[36m\033[1m" + d.name + "\033[0m");
            out.println("  \033[90m" + shortDesc + "\033[0m");
            out.println();
        });
    }

    private void handleModels(AgentContext ctx) {
        if (ctx == null || ctx.config == null || ctx.config.providers == null) {
            out.println("No providers configured.");
            return;
        }
        String current = ctx.provider.getClass().getSimpleName().replace("Provider", "").toLowerCase();
        out.println("Configured providers:");
        ctx.config.providers.forEach((name, pc) -> {
            String marker = name.equals(current) ? " ◀ current" : "";
            out.println("  " + name + "  (" + (pc.model != null ? pc.model : "no model") + ")" + marker);
        });
        out.println("Use /model <name> to switch.");
    }

    private void handleModel(String arg, AgentContext ctx) {
        if (arg == null || arg.isBlank()) {
            out.println("Usage: /model <name>  (available: anthropic, openai, ollama, minimax)");
            if (ctx != null) out.println("Current: " + ctx.provider.getClass().getSimpleName()
                .replace("Provider", "").toLowerCase());
            return;
        }
        if (providerFactory == null) { out.println("Provider switching not available."); return; }
        try {
            LLMProvider newProvider = providerFactory.apply(arg);
            if (newProvider == null) { out.println("Unknown provider: " + arg); return; }
            if (ctx != null) ctx.provider = newProvider;
            out.println("Switched to: " + arg);
        } catch (Exception e) {
            out.println("Failed to switch provider: " + e.getMessage());
        }
    }

    private void handleSessions() {
        if (historyStore == null) { out.println("History not available."); return; }
        try {
            List<String> sessions = historyStore.listSessions();
            if (sessions.isEmpty()) { out.println("No sessions found."); return; }
            out.println("Recent sessions (newest first):");
            sessions.stream().limit(20).forEach(s -> out.println("  " + s));
            out.println("Use /history <sessionId> to view details.");
        } catch (Exception e) {
            out.println("Error listing sessions: " + e.getMessage());
        }
    }

    private void handleHistory(String arg) {
        if (historyStore == null) { out.println("History not available."); return; }
        try {
            if (arg == null) {
                List<String> sessions = historyStore.listSessions();
                if (sessions.isEmpty()) { out.println("No sessions found."); return; }
                out.println("Recent sessions (newest first):");
                sessions.stream().limit(10).forEach(s -> out.println("  " + s));
                out.println("Use /history <sessionId> to view details.");
            } else {
                String sessionId = "last".equals(arg)
                    ? historyStore.listSessions().stream().findFirst().orElse(null)
                    : arg;
                if (sessionId == null) { out.println("No sessions found."); return; }
                List<HistoryEvent> events = historyStore.readSession(sessionId);
                if (events.isEmpty()) { out.println("Session not found or empty: " + sessionId); return; }
                new HistoryRenderer().render(events, out);
            }
        } catch (Exception e) {
            out.println("Error reading history: " + e.getMessage());
        }
    }

    private void handleSkillCommand(String arg, AgentContext ctx) {
        SkillManager sm = (ctx != null) ? ctx.skillManager : skillManager;
        if (arg == null || arg.isBlank()) {
            out.println("Usage: /skill <name> | /skill refresh");
            return;
        }
        if (sm == null) { out.println("No active session."); return; }
        if ("refresh".equals(arg.trim())) {
            sm.refresh();
        } else {
            String result = sm.activate(arg.trim(), ctx != null ? ctx.workingDir : null);
            out.println(result);
        }
    }

    private void handleSkills(AgentContext ctx) {
        SkillManager sm = (ctx != null) ? ctx.skillManager : skillManager;
        if (sm == null || sm.getLoaded().isEmpty()) {
            out.println("No skills loaded. Place SKILL.md files in .self-agent/skills/<name>/");
            return;
        }
        out.println("Skills (" + sm.getLoaded().size() + " loaded):");
        for (SkillDefinition s : sm.getLoaded()) {
            String sourceTag = "learned".equals(s.source) ? " \033[90m[learned]\033[0m" : "";
            out.println("  " + s.name + sourceTag + " — " + s.description);
        }
    }

    private void handleMemory(String arg, AgentContext ctx) {
        if (ctx == null) { out.println("No active session."); return; }
        if ("long".equals(arg)) {
            try {
                List<MemoryEntry> entries = ctx.persistentMemory.loadIndex();
                if (entries.isEmpty()) { out.println("No long-term memories."); return; }
                out.println("Long-term memories (" + entries.size() + "):");
                entries.forEach(e -> out.println("  [" + e.type + "] " + e.name + " — " + e.description));
            } catch (Exception e) { out.println("Error: " + e.getMessage()); }
        } else {
            var sessionEntries = ctx.sessionMemory.getAll();
            if (sessionEntries.isEmpty()) {
                out.println("Session memory is empty.");
            } else {
                out.println("Session memory (" + sessionEntries.size() + " entries):");
                sessionEntries.forEach((k, v) -> out.println("  " + k + ": " + v));
            }
        }
    }

    private void handleRemember(String arg, AgentContext ctx) {
        if (ctx == null || arg == null || arg.isBlank()) {
            out.println("Usage: /remember <content>");
            return;
        }
        try {
            // 调用 LLM 生成 name、description、type
            String prompt = "根据以下内容，生成一条结构化记忆条目。\n" +
                "只输出 JSON，格式：{\"name\":\"xxx\",\"description\":\"一行摘要不超过60字\",\"type\":\"user|feedback|project|reference|task\"}\n\n" +
                "内容：" + arg;
            com.selfagent.model.ChatRequest req = new com.selfagent.model.ChatRequest(
                List.of(Map.of("role", "user", "content", prompt)),
                List.of(), null, false, null);
            com.selfagent.model.ChatResponse resp = ctx.provider.chat(req);

            String name = "manual_" + System.currentTimeMillis();
            String description = arg.length() > 60 ? arg.substring(0, 60) : arg;
            String type = "project";

            if (resp.content != null && !resp.content.isBlank()) {
                try {
                    int start = resp.content.indexOf('{');
                    int end = resp.content.lastIndexOf('}');
                    if (start >= 0 && end > start) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(resp.content.substring(start, end + 1));
                        if (node.has("name") && !node.get("name").asText().isBlank())
                            name = node.get("name").asText();
                        if (node.has("description") && !node.get("description").asText().isBlank())
                            description = node.get("description").asText();
                        if (node.has("type") && !node.get("type").asText().isBlank())
                            type = node.get("type").asText();
                    }
                } catch (Exception ignored) {}
            }

            MemoryEntry entry = new MemoryEntry(name, description, type, arg);
            ctx.persistentMemory.save(entry);
            out.println("[Memory] Saved: " + name + " — " + description);
        } catch (Exception e) { out.println("Error: " + e.getMessage()); }
    }

    private void handleForget(String arg, AgentContext ctx) {
        if (ctx == null || arg == null || arg.isBlank()) {
            out.println("Usage: /forget <keyword>");
            return;
        }
        try {
            ctx.persistentMemory.delete(arg);
            out.println("[Memory] Deleted entries matching: " + arg);
        } catch (Exception e) { out.println("Error: " + e.getMessage()); }
    }

    private void handleMcp(String arg) {
        if (arg == null) { listMcpServers(); return; }
        String[] parts = arg.split("\\s+", 2);
        String sub = parts[0];
        String name = parts.length > 1 ? parts[1] : null;

        switch (sub) {
            case "enable" -> {
                if (name == null) { out.println("Usage: /mcp enable <server-name>"); return; }
                enableServer(name);
            }
            case "disable" -> {
                if (name == null) { out.println("Usage: /mcp disable <server-name>"); return; }
                disableServer(name);
            }
            case "refresh" -> refreshServers();
            default -> expandServer(sub);
        }
    }

    private void listMcpServers() {
        Map<String, List<ToolDefinition>> byServer = registry.getMcpToolsByServer();
        if (byServer.isEmpty()) {
            out.println("No MCP servers connected. Use /mcp refresh to load from config.");
            return;
        }
        out.println("MCP servers (" + byServer.size() + " connected):");
        byServer.forEach((name, tools) ->
            out.println("  [on]  " + name + " (" + tools.size() + " tools)  — /mcp " + name));
    }

    private void expandServer(String serverName) {
        Map<String, List<ToolDefinition>> byServer = registry.getMcpToolsByServer();
        List<ToolDefinition> tools = byServer.get(serverName);
        if (tools == null) {
            out.println("MCP server not connected: " + serverName);
        } else {
            out.println(serverName + " tools (" + tools.size() + "):");
            tools.forEach(d -> out.println("  " + d.name + " — " + d.description));
        }
    }

    private void enableServer(String name) {
        try {
            McpConfigEditor.setEnabled(name, true);
            AgentConfig.McpServerConfig cfg = findServerConfig(name);
            if (cfg == null) { out.println("Server '" + name + "' not found in config.mcp.json"); return; }
            cfg.enabled = true;
            boolean ok = mcpManager.connect(cfg);
            if (ok) out.println("[MCP] " + name + " enabled and connected.");
            else out.println("[MCP] Config updated but connection failed.");
        } catch (Exception e) {
            out.println("Failed to enable " + name + ": " + e.getMessage());
        }
    }

    private void disableServer(String name) {
        try {
            boolean disconnected = mcpManager.disconnect(name);
            McpConfigEditor.setEnabled(name, false);
            if (disconnected) out.println("[MCP] " + name + " disabled and disconnected.");
            else out.println("[MCP] " + name + " was not connected, marked disabled in config.");
        } catch (Exception e) {
            out.println("Failed to disable " + name + ": " + e.getMessage());
        }
    }

    private void refreshServers() {
        try {
            String result = mcpManager.refresh();
            out.println("[MCP] " + result);
        } catch (Exception e) {
            out.println("Refresh failed: " + e.getMessage());
        }
    }

    private AgentConfig.McpServerConfig findServerConfig(String name) {
        try {
            return AgentConfig.loadMcpServers(
                java.nio.file.Paths.get(System.getProperty("user.home"), ".self-agent", "config.mcp.json")
            ).stream().filter(s -> name.equals(s.name)).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleImport(String arg, AgentContext ctx) {
        if (arg == null || arg.isBlank()) { out.println("Usage: /import <path|url>"); return; }
        if (ctx == null) { out.println("No active session."); return; }
        AgentConfig.ProviderConfig embPc = ctx.config.rag != null && ctx.config.rag.embeddingProvider != null
            ? ctx.config.providers.get(ctx.config.rag.embeddingProvider)
            : ctx.config.providers.get(ctx.config.defaultProvider);
        if (embPc == null) { out.println("No embedding provider configured."); return; }
        EmbeddingService embedding = new LLMEmbeddingService(embPc.baseUrl, embPc.apiKey, embPc.model);
        DocumentLoader loader = new DocumentLoader();
        ChunkSplitter splitter = new ChunkSplitter();
        try {
            String text;
            String source;
            if (arg.startsWith("http://") || arg.startsWith("https://")) {
                out.println("Fetching " + arg + " ...");
                text = loader.loadUrl(arg);
                source = arg;
            } else {
                java.nio.file.Path path = java.nio.file.Path.of(arg);
                out.println("Loading " + path.getFileName() + " ...");
                text = loader.loadFile(path);
                source = path.getFileName().toString();
            }
            String ext = source.contains(".") ? source.substring(source.lastIndexOf('.') + 1) : "text";
            String sourceType = "md".equals(ext) ? "md" : "text";
            java.util.List<String> chunks = splitter.split(text, sourceType);
            out.println("Splitting into " + chunks.size() + " chunks, generating embeddings...");
            java.util.List<float[]> vectors = new java.util.ArrayList<>();
            for (String chunk : chunks) vectors.add(embedding.embed(chunk));
            String docId = source + "_" + System.currentTimeMillis();
            ctx.ragStore.add(docId, source, chunks, vectors);
            ctx.ragStore.save();
            out.println("Imported " + chunks.size() + " chunks from " + source);
        } catch (Exception e) {
            out.println("Import failed: " + e.getMessage());
        }
    }

    private void handleRag(String arg, AgentContext ctx) {
        if (ctx == null) { out.println("No active session."); return; }
        if ("on".equals(arg)) {
            ctx.ragStore.setEnabled(true);
            try { ctx.ragStore.saveConfig(); } catch (Exception e) { out.println("Save failed: " + e.getMessage()); }
            out.println("RAG enabled.");
        } else if ("off".equals(arg)) {
            ctx.ragStore.setEnabled(false);
            try { ctx.ragStore.saveConfig(); } catch (Exception e) { out.println("Save failed: " + e.getMessage()); }
            out.println("RAG disabled.");
        } else {
            out.println("RAG status: " + (ctx.ragStore.isEnabled() ? "enabled" : "disabled"));
            out.println("Indexed chunks: " + ctx.ragStore.size());
            java.util.List<String> docs = ctx.ragStore.listDocIds();
            if (docs.isEmpty()) {
                out.println("No documents imported. Use /import <path|url> to add documents.");
            } else {
                out.println("Imported documents:");
                docs.forEach(d -> out.println("  - " + d));
            }
        }
    }

    private void handleTiming(String arg) {
        if ("on".equals(arg)) {
            TimingLogger.setEnabled(true);
            out.println("Timing logs enabled (persisted).");
        } else if ("off".equals(arg)) {
            TimingLogger.setEnabled(false);
            out.println("Timing logs disabled (persisted).");
        } else {
            out.println("Timing logs: " + (TimingLogger.isEnabled() ? "enabled" : "disabled"));
            out.println("Usage: /timing on|off");
        }
    }

    private void handleThinking(String arg) {
        if ("on".equals(arg)) {
            ThinkingLogger.setEnabled(true);
            out.println("Thinking display enabled (persisted).");
        } else if ("off".equals(arg)) {
            ThinkingLogger.setEnabled(false);
            out.println("Thinking display disabled (persisted).");
        } else {
            out.println("Thinking display: " + (ThinkingLogger.isEnabled() ? "enabled" : "disabled"));
            out.println("Usage: /thinking on|off");
        }
    }

    private void handleSandbox(String arg, AgentContext ctx) {
        if (ctx == null || ctx.config == null) { out.println("No active session."); return; }
        com.selfagent.sandbox.SandboxConfig sc = ctx.config.sandbox;
        if ("on".equals(arg)) {
            sc.enabled = true;
            ctx.activeSandboxRuntime = com.selfagent.sandbox.SandboxRuntime.detect();
            out.println("Sandbox enabled.");
            out.println("  ✓ Command approval takes effect immediately.");
            out.println("  ✓ OS-level isolation: " + ctx.activeSandboxRuntime.getClass().getSimpleName());
        } else if ("off".equals(arg)) {
            sc.enabled = false;
            out.println("Sandbox disabled.");
        } else {
            out.println("Sandbox: " + (sc.enabled ? "enabled" : "disabled"));
            com.selfagent.sandbox.SandboxRuntime activeRuntime =
                ctx.activeSandboxRuntime != null ? ctx.activeSandboxRuntime : ctx.sandboxRuntime;
            out.println("Runtime: " + activeRuntime.getClass().getSimpleName());
            out.println("Allow network: " + sc.allowNetwork);
            out.println("Allow commands: " + sc.allowCommands);
            out.println("Deny write paths: " + sc.denyWritePaths);
            out.println("Usage: /sandbox on|off");
        }
    }

    private void handleAgent(String arg, AgentContext ctx) {
        if (ctx == null || ctx.orchestrator == null) { out.println("No active session."); return; }
        if (arg == null || arg.isBlank()) {
            out.println("Usage: /agent <task>  or  /agent <type> <task>");
            out.println("Available types: " + ctx.agentRegistry.listAll().stream()
                .map(d -> d.name).toList());
            return;
        }
        String[] parts = arg.split("\\s+", 2);
        String subagentType = "general-purpose";
        String prompt = arg;
        if (parts.length > 1 && ctx.agentRegistry.find(parts[0]) != null) {
            subagentType = parts[0];
            prompt = parts[1];
        }
        com.selfagent.agent.multi.SubAgentTask task =
            new com.selfagent.agent.multi.SubAgentTask(subagentType, prompt, prompt, null, false);
        com.selfagent.agent.multi.SubAgentResult result = ctx.orchestrator.run(task, ctx);
        out.println(result.toToolResult());
    }

    private void handleVerify(String arg, AgentContext ctx) {
        if (ctx == null || ctx.selfcheckManager == null) {
            out.println("Selfcheck not available.");
            return;
        }
        if (ctx.orchestrator == null) {
            out.println("Multi-agent not initialized. Selfcheck requires orchestrator.");
            return;
        }
        out.println("\033[90m[Selfcheck] Starting verification agent...\033[0m");
        com.selfagent.selfcheck.VerdictParser.Verdict verdict =
            ctx.selfcheckManager.runVerification(arg, ctx, ctx.contextManager);
        if (verdict == com.selfagent.selfcheck.VerdictParser.Verdict.UNKNOWN) {
            out.println("\033[90m[Selfcheck] Could not determine verdict. Check output above.\033[0m");
        }
    }
}
