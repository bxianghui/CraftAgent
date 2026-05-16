package com.selfagent.cli;

import com.selfagent.agent.*;
import com.selfagent.agent.multi.AgentOrchestrator;
import com.selfagent.agent.multi.AgentRegistry;
import com.selfagent.common.JacksonUtil;
import com.selfagent.common.ModelProvider;
import com.selfagent.memory.MemoryEntry;
import com.selfagent.memory.MemoryExtractor;
import com.selfagent.memory.MemoryInjector;
import com.selfagent.model.*;
import com.selfagent.common.TimingLogger;
import com.selfagent.rag.*;
import com.selfagent.tool.McpManager;
import com.selfagent.tool.ToolRegistry;
import com.selfagent.tool.builtin.*;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "self-agent", mixinStandardHelpOptions = true, version = "1.0",
    description = "AI-powered coding assistant",
    subcommands = {CliApp.RunCommand.class})
public class CliApp implements Callable<Integer> {

    @Option(names = {"--model"}, description = "Provider name: anthropic, openai, ollama, minimax, kimi, deepseek")
    String model;

    @Option(names = {"--resume"}, description = "Resume last session")
    boolean resume;

    @Option(names = {"--yes", "-y"}, description = "Auto-approve non-dangerous operations")
    boolean autoApprove;

    @Override
    public Integer call() throws Exception {
        AgentContext ctx = buildContext();
        // config.yaml 的 log.timing_log 作为初始值，但 timing.enabled 文件优先（运行时持久化）
        if (ctx.config.log != null && ctx.config.log.timingLog && !TimingLogger.isEnabled()) {
            TimingLogger.setEnabled(true);
        }
        ToolRegistry registry = buildRegistry();
        // 初始化 RAG 模块
        if (ctx.ragStore.isEnabled()) {
            AgentConfig.ProviderConfig embPc = ctx.config.rag != null && ctx.config.rag.embeddingProvider != null
                ? ctx.config.providers.get(ctx.config.rag.embeddingProvider)
                : ctx.config.providers.get(ctx.config.defaultProvider);
            if (embPc != null) {
                EmbeddingService embedding = new LLMEmbeddingService(embPc.baseUrl, embPc.apiKey, embPc.model);
                QueryDecomposer decomposer = new QueryDecomposer(ctx.provider);
                registry.register(new RagSearchTool(ctx.ragStore, embedding, decomposer));
            }
        }
        // 初始化多 Agent 模块
        ctx.toolRegistry = registry;
        ctx.agentRegistry = AgentRegistry.create(ctx.workingDir);
        ctx.orchestrator = new AgentOrchestrator(ctx.agentRegistry);
        registry.register(new com.selfagent.agent.multi.AgentTool(ctx));
        // 初始化 Hook 模块
        ctx.hookManager = com.selfagent.hook.HookManager.load(ctx.provider);
        ctx.selfcheckManager = new com.selfagent.selfcheck.SelfcheckManager(ctx.orchestrator);
        ctx.learnedSkillExtractor = com.selfagent.skill.LearnedSkillExtractor.createDefault(ctx.provider);
        McpManager mcpManager = new McpManager(ctx.config, registry);
        mcpManager.registerAll();
        ContextManager cm = new ContextManager(ctx.provider.maxTokens(), ctx.config.context.maxTokenRatio, ctx.config.context.keepRecentTurns);
        cm.setCustomSystemPrompt(resolveSystemPrompt(ctx.config));
        ctx.contextManager = cm;
        // 触发 SessionStart
        if (ctx.hookManager.hasHooks("SessionStart")) {
            com.selfagent.hook.HookInput startInput = new com.selfagent.hook.HookInput(
                "SessionStart", ctx.sessionId, null, null, null, false, null,
                resume ? "resume" : "startup", ctx.workingDir.toString());
            com.selfagent.hook.HookOutput startOut = ctx.hookManager.fire("SessionStart", startInput);
            if (startOut.additionalContext() != null) {
                cm.setSystemPromptSuffix(startOut.additionalContext());
            }
            if (startOut.initialUserMessage() != null && !startOut.initialUserMessage().isBlank()) {
                cm.addUserMessage(startOut.initialUserMessage());
            }
        }
        MemoryInjector injector = new MemoryInjector(ctx.persistentMemory, ctx.provider);
        MemoryExtractor extractor = new MemoryExtractor(ctx.provider);
        ReactLoop loop = new ReactLoop(registry, cm, injector, extractor);
        AgentConfig finalConfig = ctx.config;
        SlashCommandRouter router = new SlashCommandRouter(registry, cm, mcpManager, ctx.historyStore,
            name -> buildProvider(name, finalConfig), ctx.skillManager, System.out);
        new ReplLoop(loop, cm, router).start(ctx);
        // 触发 SessionEnd（异步，不阻塞退出）
        if (ctx.hookManager != null && ctx.hookManager.hasHooks("SessionEnd")) {
            com.selfagent.hook.HookInput endInput = new com.selfagent.hook.HookInput(
                "SessionEnd", ctx.sessionId, null, null, null, false, null,
                null, ctx.workingDir.toString());
            ctx.hookManager.fireAsync("SessionEnd", endInput);
        }
        ctx.close();
        if (ctx.orchestrator != null) ctx.orchestrator.shutdown();
        promoteMemory(ctx, extractor, cm);
        // 异步提炼 learned skill（不阻塞退出）
        if (ctx.learnedSkillExtractor != null && ctx.sessionTracker != null) {
            final var history = cm.buildMessages();
            final var tracker = ctx.sessionTracker;
            final var learner = ctx.learnedSkillExtractor;
            java.util.concurrent.CompletableFuture.runAsync(() -> learner.extract(history, tracker));
        }
        mcpManager.closeAll();
        return 0;
    }

    AgentContext buildContext() throws IOException {
        AgentConfig config = loadConfig();
        String providerName = model != null ? model : config.defaultProvider;
        LLMProvider provider = buildProvider(providerName, config);
        return new AgentContext(provider, Path.of(System.getProperty("user.dir")), autoApprove, config);
    }

    private AgentConfig loadConfig() {
        try {
            return AgentConfig.loadDefault();
        } catch (IOException e) {
            AgentConfig defaults = new AgentConfig();
            defaults.defaultProvider = "minimax";
            defaults.providers = new HashMap<>();
            AgentConfig.ProviderConfig pc = new AgentConfig.ProviderConfig();
            pc.apiKey = System.getenv("MINIMAX_API_KEY");
            pc.model = "MiniMax-M2.7";
            pc.baseUrl = "https://api.minimaxi.com/anthropic";
            pc.temperature = 0.8f;
            defaults.providers.put("minimax", pc);
            defaults.context = new AgentConfig.ContextConfig();
            return defaults;
        }
    }

    private LLMProvider buildProvider(String name, AgentConfig config) {
        AgentConfig.ProviderConfig pc = config.providers != null ? config.providers.get(name) : null;
        if (pc == null) return null;
        try {
            Reflections reflections = new Reflections("com.selfagent.model");
            Set<Class<? extends LLMProvider>> modelProviders = reflections.getSubTypesOf(LLMProvider.class);
            Class<? extends LLMProvider> modelProvider = modelProviders.stream()
                    .filter(cla -> cla.getAnnotation(ModelProvider.class) != null && cla.getAnnotation(ModelProvider.class).name().equals(name))
                    .findFirst()
                    .orElse(null);
            Constructor<? extends LLMProvider> constructor = modelProvider.getDeclaredConstructor(String.class, String.class, String.class, float.class);
            constructor.setAccessible(true);
            return constructor.newInstance(pc.apiKey, pc.model, pc.baseUrl, pc.temperature);
        }catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }catch (Exception e) {
            throw new RuntimeException("model provider constructor error", e);
        }
    }

    ToolRegistry buildRegistry() {
        ToolRegistry r = new ToolRegistry();
        r.register(new ReadFileTool());
        r.register(new WriteFileTool());
        r.register(new EditFileTool());
        r.register(new BashTool());
        r.register(new ListFilesTool());
        r.register(new SearchCodeTool());
        r.register(new WebFetchTool());
        return r;
    }

    private String resolveSystemPrompt(AgentConfig config) {
        // system_prompt_file 优先于 system_prompt
        if (config.systemPromptFile != null && !config.systemPromptFile.isBlank()) {
            try {
                return java.nio.file.Files.readString(java.nio.file.Path.of(config.systemPromptFile));
            } catch (Exception e) {
                System.err.println("[Config] Failed to read system_prompt_file: " + e.getMessage());
            }
        }
        if (config.systemPrompt != null && !config.systemPrompt.isBlank()) {
            return config.systemPrompt;
        }
        return null;
    }

    private void promoteMemory(AgentContext ctx, MemoryExtractor extractor, ContextManager cm) {
        // 后台异步提炼，避免阻塞退出；JVM shutdown hook 等待完成（最多 60 秒）
        java.util.List<java.util.Map<String, Object>> history = cm.buildMessages();
        java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(
            () -> extractor.promoteFromHistory(history, ctx.persistentMemory));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { future.get(60, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception ignored) {}
        }, "memory-promote-shutdown"));
    }

    @Command(name = "run", description = "Non-interactive single prompt execution")
    static class RunCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Prompt to execute") String prompt;
        @Option(names = {"--model"}) String model;
        @Option(names = {"--yes", "-y"}) boolean autoApprove;

        @Override
        public Integer call() throws Exception {
            CliApp parent = new CliApp();
            parent.model = model;
            parent.autoApprove = autoApprove;
            AgentContext ctx = parent.buildContext();
            ToolRegistry registry = parent.buildRegistry();
            ctx.toolRegistry = registry;
            ctx.agentRegistry = AgentRegistry.create(ctx.workingDir);
            ctx.orchestrator = new AgentOrchestrator(ctx.agentRegistry);
            McpManager mcpManager = new McpManager(ctx.config, registry);
            mcpManager.registerAll();
            ContextManager cm = new ContextManager(ctx.provider.maxTokens(), ctx.config.context.maxTokenRatio, ctx.config.context.keepRecentTurns);
            cm.setCustomSystemPrompt(parent.resolveSystemPrompt(ctx.config));
            ctx.contextManager = cm;
            ReactLoop loop = new ReactLoop(registry, cm);
            String result = loop.run(prompt, ctx);
            System.out.println(result);
            ctx.orchestrator.shutdown();
            mcpManager.closeAll();
            return 0;
        }
    }
}
