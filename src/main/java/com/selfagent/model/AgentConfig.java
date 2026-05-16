package com.selfagent.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentConfig {
    public String defaultProvider;
    public Map<String, ProviderConfig> providers;
    public ContextConfig context;
    public RagConfig rag = new RagConfig();
    public LogConfig log = new LogConfig();
    public com.selfagent.sandbox.SandboxConfig sandbox = new com.selfagent.sandbox.SandboxConfig();
    public List<McpServerConfig> mcpServers;
    public String systemPrompt;      // 直接写 system prompt 内容
    public String systemPromptFile;  // 指向外部文件路径，与 systemPrompt 互斥，文件优先

    public static class ProviderConfig {
        public String apiKey;
        public String model;
        public String baseUrl;
        public float temperature;
    }

    public static class ContextConfig {
        public double maxTokenRatio = 0.8;
        public int keepRecentTurns = 20;
        public int memoryPromoteInterval = 20;
    }

    public static class RagConfig {
        public boolean enabled = true;
        public String embeddingProvider;
    }

    public static class LogConfig {
        public boolean timingLog = false;
    }

    public static class McpServerConfig {
        public String name;
        public String transport = "stdio";  // stdio | sse | http
        public List<String> command;        // stdio only
        public Map<String, String> env;     // stdio only
        public String url;                  // sse/http only
        public boolean enabled = true;
    }

    @SuppressWarnings("unchecked")
    public static AgentConfig load(Path configPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configPath)) {
            Map<String, Object> raw = yaml.load(in);
            AgentConfig cfg = new AgentConfig();
            cfg.defaultProvider = (String) raw.get("default_provider");

            cfg.providers = new HashMap<>();
            Map<String, Map<String, Object>> rawProviders =
                (Map<String, Map<String, Object>>) raw.get("providers");
            if (rawProviders != null) {
                rawProviders.forEach((name, vals) -> {
                    ProviderConfig pc = new ProviderConfig();
                    pc.apiKey = (String) vals.get("api_key");
                    pc.model = (String) vals.get("model");
                    pc.baseUrl = (String) vals.get("base_url");
                    if (vals.containsKey("temperature"))
                        pc.temperature = ((Number) vals.get("temperature")).floatValue();
                    cfg.providers.put(name, pc);
                });
            }

            cfg.context = new ContextConfig();
            Map<String, Object> rawCtx = (Map<String, Object>) raw.get("context");
            if (rawCtx != null) {
                if (rawCtx.containsKey("max_token_ratio"))
                    cfg.context.maxTokenRatio = ((Number) rawCtx.get("max_token_ratio")).doubleValue();
                if (rawCtx.containsKey("keep_recent_turns"))
                    cfg.context.keepRecentTurns = ((Number) rawCtx.get("keep_recent_turns")).intValue();
                if (rawCtx.containsKey("memory_promote_interval"))
                    cfg.context.memoryPromoteInterval = ((Number) rawCtx.get("memory_promote_interval")).intValue();
            }

            cfg.rag = new RagConfig();
            Map<String, Object> rawRag = (Map<String, Object>) raw.get("rag");
            if (rawRag != null) {
                if (rawRag.containsKey("enabled"))
                    cfg.rag.enabled = (Boolean) rawRag.get("enabled");
                if (rawRag.containsKey("embedding_provider"))
                    cfg.rag.embeddingProvider = (String) rawRag.get("embedding_provider");
            }

            cfg.log = new LogConfig();
            Map<String, Object> rawLog = (Map<String, Object>) raw.get("log");
            if (rawLog != null) {
                if (rawLog.containsKey("timing_log"))
                    cfg.log.timingLog = (Boolean) rawLog.get("timing_log");
            }

            cfg.sandbox = new com.selfagent.sandbox.SandboxConfig();
            Map<String, Object> rawSandbox = (Map<String, Object>) raw.get("sandbox");
            if (rawSandbox != null) {
                if (rawSandbox.containsKey("enabled"))
                    cfg.sandbox.enabled = (Boolean) rawSandbox.get("enabled");
                if (rawSandbox.containsKey("allow_network"))
                    cfg.sandbox.allowNetwork = (Boolean) rawSandbox.get("allow_network");
                if (rawSandbox.containsKey("allow_commands"))
                    cfg.sandbox.allowCommands = (List<String>) rawSandbox.get("allow_commands");
                if (rawSandbox.containsKey("deny_write_paths"))
                    cfg.sandbox.denyWritePaths = (List<String>) rawSandbox.get("deny_write_paths");
            }

            cfg.systemPrompt = (String) raw.get("system_prompt");
            cfg.systemPromptFile = (String) raw.get("system_prompt_file");

            // mcp_servers 从独立的 config.mcp.json 加载，忽略 config.yaml 中的 mcp_servers
            Path mcpConfigPath = configPath.getParent().resolve("config.mcp.json");
            try {
                cfg.mcpServers = loadMcpServers(mcpConfigPath);
            } catch (Exception e) {
                System.err.println("[MCP] Failed to load config.mcp.json, skipping: " + e.getMessage());
                cfg.mcpServers = new ArrayList<>();
            }

            return cfg;
        }
    }

    public static List<McpServerConfig> loadMcpServers(Path mcpConfigPath) throws IOException {
        if (!Files.exists(mcpConfigPath)) return new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(mcpConfigPath.toFile(),
            new TypeReference<List<McpServerConfig>>() {});
    }

    public static AgentConfig loadDefault() throws IOException {
        Path p = Paths.get(System.getProperty("user.home"), ".self-agent", "config.yaml");
        return load(p);
    }
}
