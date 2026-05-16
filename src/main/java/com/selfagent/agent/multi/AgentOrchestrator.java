package com.selfagent.agent.multi;

import com.selfagent.agent.AgentContext;
import com.selfagent.agent.ReactLoop;
import com.selfagent.common.ToolDisplay;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class AgentOrchestrator {
    private static final Path TASKS_DIR =
        Paths.get(System.getProperty("user.home"), ".self-agent", "tasks");
    private final AgentRegistry registry;
    private final ExecutorService pool;
    private static final int TIMEOUT_MINUTES = 5;

    public AgentOrchestrator(AgentRegistry registry) {
        this.registry = registry;
        this.pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "sub-agent");
            t.setDaemon(true);
            return t;
        });
    }

    /** 前台同步执行（阻塞主 agent） */
    public SubAgentResult run(SubAgentTask task, AgentContext parentCtx) {
        String agentId = generateAgentId(task.subagentType());
        long start = System.currentTimeMillis();
        String desc = task.description() != null && !task.description().isBlank()
            ? task.description() : task.prompt();
        ToolDisplay.agentStart(task.subagentType(), desc);
        try {
            String result = execute(task, parentCtx, agentId, null);
            long duration = System.currentTimeMillis() - start;
            ToolDisplay.agentSuccess(task.subagentType(), desc, duration);
            return new SubAgentResult(agentId, task.subagentType(), desc, result, null, true, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            ToolDisplay.agentFailure(task.subagentType(), desc, e.getMessage());
            return new SubAgentResult(agentId, task.subagentType(), desc,
                "Agent failed: " + e.getMessage(), null, false, duration);
        }
    }

    /** 后台异步执行（不阻塞主 agent），输出实时写入 outputFile */
    public CompletableFuture<SubAgentResult> submit(SubAgentTask task, AgentContext parentCtx) {
        String agentId = generateAgentId(task.subagentType());
        String outputFile = prepareOutputFile(agentId);
        String desc = task.description() != null && !task.description().isBlank()
            ? task.description() : task.prompt();
        com.selfagent.common.ToolDisplay.bgAgentStart(task.subagentType(), desc);
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                String result = execute(task, parentCtx, agentId, outputFile);
                long duration = System.currentTimeMillis() - start;
                appendToOutputFile(outputFile, "\n[Agent completed in " + duration + "ms]");
                com.selfagent.common.ToolDisplay.bgAgentSuccess(task.subagentType(), desc, duration);
                return new SubAgentResult(agentId, task.subagentType(), desc, result, outputFile, true, duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                appendToOutputFile(outputFile, "\n[Agent failed: " + e.getMessage() + "]");
                com.selfagent.common.ToolDisplay.bgAgentFailure(task.subagentType(), desc, e.getMessage());
                return new SubAgentResult(agentId, task.subagentType(), desc,
                    "Agent failed: " + e.getMessage(), outputFile, false, duration);
            }
        }, pool);
    }

    private String execute(SubAgentTask task, AgentContext parentCtx, String agentId, String outputFile) {
        AgentDefinition def = "auto".equals(task.subagentType())
            ? registry.createTemporary(task.autoSystemPrompt())
            : registry.find(task.subagentType());
        if (def == null) {
            throw new IllegalArgumentException("Unknown agent type: " + task.subagentType()
                + ". Available: " + registry.listAll().stream().map(d -> d.name).toList());
        }
        List<Map<String, Object>> parentHistory = parentCtx.contextManager != null
            ? new ArrayList<>(parentCtx.contextManager.buildMessages()) : List.of();
        SubAgentContext subCtx = new SubAgentContext(parentCtx, def, parentHistory);
        AgentContext subAgentCtx = buildSubAgentCtx(parentCtx, subCtx);
        ReactLoop subLoop = new ReactLoop(subCtx.toolRegistry, subCtx.contextManager);

        if (outputFile != null) {
            // 后台模式：onChunk 写入 outputFile
            StringBuilder result = new StringBuilder();
            subLoop.streamRun(task.prompt(), subAgentCtx, chunk -> {
                result.append(chunk);
                appendToOutputFile(outputFile, chunk);
            });
            return result.toString();
        } else {
            return subLoop.run(task.prompt(), subAgentCtx);
        }
    }

    private static final int MAX_DEPTH = 1;

    private AgentContext buildSubAgentCtx(AgentContext parentCtx, SubAgentContext subCtx) {
        AgentContext sub = new AgentContext();
        sub.provider = parentCtx.provider;
        sub.workingDir = parentCtx.workingDir;
        sub.autoApprove = parentCtx.autoApprove;
        sub.config = parentCtx.config;
        // 子 agent 独立 HistoryStore，避免并发写冲突
        try {
            sub.historyStore = com.selfagent.history.HistoryStore.defaultStore();
        } catch (Exception e) {
            sub.historyStore = parentCtx.historyStore;
        }
        // 子 agent 独立 SessionLogger，避免多线程并发写同一实例导致记录混乱
        try {
            sub.sessionLogger = com.selfagent.history.SessionLogger.defaultLogger();
        } catch (Exception e) {
            sub.sessionLogger = parentCtx.sessionLogger;
        }
        sub.sessionMemory = parentCtx.sessionMemory;
        sub.persistentMemory = parentCtx.persistentMemory;
        sub.skillManager = parentCtx.skillManager;
        sub.skillWatcher = parentCtx.skillWatcher;
        sub.ragStore = parentCtx.ragStore;
        sub.sandboxRuntime = parentCtx.sandboxRuntime;
        sub.activeSandboxRuntime = parentCtx.activeSandboxRuntime;
        sub.lineReader = parentCtx.lineReader;
        sub.terminalWriter = parentCtx.terminalWriter;
        sub.toolRegistry = subCtx.toolRegistry;
        sub.contextManager = subCtx.contextManager;
        sub.agentRegistry = parentCtx.agentRegistry;
        sub.subAgentDepth = parentCtx.subAgentDepth + 1;
        // 未超过最大深度则允许继续派发子 agent
        sub.orchestrator = sub.subAgentDepth < MAX_DEPTH ? parentCtx.orchestrator : null;
        return sub;
    }

    private String generateAgentId(String agentType) {
        return "agent_" + agentType + "_" + System.currentTimeMillis();
    }

    private String prepareOutputFile(String agentId) {
        try {
            Files.createDirectories(TASKS_DIR);
            Path output = TASKS_DIR.resolve(agentId + ".output");
            Files.writeString(output, "");
            return output.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private void appendToOutputFile(String outputFile, String content) {
        if (outputFile == null) return;
        try {
            Files.writeString(Path.of(outputFile), content,
                java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException ignored) {}
    }

    public void shutdown() {
        pool.shutdown();
    }
}
