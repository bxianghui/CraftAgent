package com.selfagent.agent;

import com.selfagent.history.HistoryStore;
import com.selfagent.history.SessionLogger;
import com.selfagent.memory.PersistentMemory;
import com.selfagent.memory.SessionMemory;
import com.selfagent.model.AgentConfig;
import com.selfagent.model.LLMProvider;
import com.selfagent.rag.RagStore;
import com.selfagent.sandbox.FallbackSandboxRuntime;
import com.selfagent.sandbox.SandboxRuntime;
import com.selfagent.skill.SkillManager;
import com.selfagent.skill.SkillWatcher;
import org.jline.reader.LineReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class AgentContext {
    public final String sessionId;
    public LLMProvider provider;
    public Path workingDir;
    public boolean autoApprove;
    public AgentConfig config;
    public HistoryStore historyStore;
    public SessionLogger sessionLogger;
    public SessionMemory sessionMemory;
    public PersistentMemory persistentMemory;
    public SkillManager skillManager;
    public SkillWatcher skillWatcher;
    public RagStore ragStore;
    public SandboxRuntime sandboxRuntime;
    public volatile SandboxRuntime activeSandboxRuntime;
    public LineReader lineReader;
    public PrintWriter terminalWriter;
    // 多 Agent 模块（CliApp 初始化后设置）
    public com.selfagent.tool.ToolRegistry toolRegistry;
    public ContextManager contextManager;
    public com.selfagent.agent.multi.AgentRegistry agentRegistry;
    public com.selfagent.agent.multi.AgentOrchestrator orchestrator;
    public int subAgentDepth = 0;  // 0 = 主 agent，最大 2
    public com.selfagent.hook.HookManager hookManager;
    public com.selfagent.selfcheck.SelfcheckManager selfcheckManager;
    public com.selfagent.skill.SessionTracker sessionTracker =
        new com.selfagent.skill.SessionTracker();
    public com.selfagent.skill.LearnedSkillExtractor learnedSkillExtractor;
    // 跨轮次的后台 agent 任务，非阻塞模式下注册于此，由 prepareRun 每轮检查
    public final java.util.List<java.util.concurrent.CompletableFuture<com.selfagent.agent.multi.SubAgentResult>>
        backgroundAgentFutures = new java.util.concurrent.CopyOnWriteArrayList<>();

    public AgentContext(LLMProvider provider, Path workingDir, boolean autoApprove,
                        AgentConfig config) throws IOException {
        this.sessionId = UUID.randomUUID().toString();
        this.provider = provider;
        this.workingDir = workingDir;
        this.autoApprove = autoApprove;
        this.config = config;
        this.historyStore = HistoryStore.defaultStore();
        this.sessionLogger = SessionLogger.defaultLogger();
        this.sessionMemory = new SessionMemory();
        this.persistentMemory = PersistentMemory.defaultMemory();
        this.skillManager = SkillManager.create(workingDir);
        this.skillManager.loadAll();
        this.skillWatcher = new SkillWatcher(skillManager);
        this.skillWatcher.start();
        this.ragStore = initRagStore(config);
        this.sandboxRuntime = initSandboxRuntime(config);
    }

    public AgentContext(String sessionId, LLMProvider provider, Path workingDir,
                        boolean autoApprove, AgentConfig config) throws IOException {
        this.sessionId = sessionId;
        this.provider = provider;
        this.workingDir = workingDir;
        this.autoApprove = autoApprove;
        this.config = config;
        this.historyStore = HistoryStore.defaultStore();
        this.sessionLogger = SessionLogger.defaultLogger();
        this.sessionMemory = new SessionMemory();
        this.persistentMemory = PersistentMemory.defaultMemory();
        this.skillManager = SkillManager.create(workingDir);
        this.skillManager.loadAll();
        this.skillWatcher = new SkillWatcher(skillManager);
        this.skillWatcher.start();
        this.ragStore = initRagStore(config);
        this.sandboxRuntime = initSandboxRuntime(config);
    }

    private static RagStore initRagStore(AgentConfig config) {
        Path ragDir = Paths.get(System.getProperty("user.home"), ".self-agent", "rag");
        try {
            RagStore store = Files.exists(ragDir.resolve("config.json"))
                ? RagStore.load(ragDir, 1536)
                : new RagStore(ragDir, 1536);
            if (config != null && config.rag != null) store.setEnabled(config.rag.enabled);
            return store;
        } catch (Exception e) {
            System.err.println("[RAG] Failed to load store, using empty: " + e.getMessage());
            return new RagStore(ragDir, 1536);
        }
    }

    /** 供 AgentOrchestrator 创建子 agent 专属上下文，字段由调用方手动赋值 */
    public AgentContext() {
        this.sessionId = UUID.randomUUID().toString();
    }

    private static SandboxRuntime initSandboxRuntime(AgentConfig config) {
        if (config != null && config.sandbox != null && config.sandbox.enabled) {
            SandboxRuntime runtime = SandboxRuntime.detect();
            System.out.println("[Sandbox] Using runtime: " + runtime.getClass().getSimpleName());
            return runtime;
        }
        return new FallbackSandboxRuntime();
    }

    public void close() {
        if (skillWatcher != null) skillWatcher.stop();
        if (historyStore != null) historyStore.flush();
        if (sessionLogger != null) sessionLogger.flush();
    }
}
