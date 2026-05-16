package com.selfagent.tool;

import com.selfagent.model.AgentConfig;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class McpManager {
    private AgentConfig config;
    private final ToolRegistry registry;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    public McpManager(AgentConfig config, ToolRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public void registerAll() {
        if (config.mcpServers == null || config.mcpServers.isEmpty()) return;
        List<AgentConfig.McpServerConfig> enabled = config.mcpServers.stream()
            .filter(s -> s.enabled)
            .toList();
        config.mcpServers.stream()
            .filter(s -> !s.enabled)
            .forEach(s -> System.out.println("[MCP] Skipping " + s.name + " (disabled)"));
        if (enabled.isEmpty()) return;

        // 并行连接所有启用的 MCP server
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(enabled.size(), 8));
        enabled.forEach(serverCfg -> pool.submit(() -> connect(serverCfg)));
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean connect(AgentConfig.McpServerConfig serverCfg) {
        try {
            McpClient client = buildClient(serverCfg);
            client.connect();
            clients.put(serverCfg.name, client);
            List<ToolDefinition> tools = client.listTools();
            for (ToolDefinition def : tools) {
                registry.register(new McpToolAdapter(client, def));
            }
            System.out.println("[MCP] " + serverCfg.name + " connected ("
                + (serverCfg.transport != null ? serverCfg.transport : "stdio")
                + "), " + tools.size() + " tools registered");
            return true;
        } catch (Exception e) {
            System.err.println("[MCP] Failed to connect " + serverCfg.name + ": " + e.getMessage());
            return false;
        }
    }

    public boolean disconnect(String serverName) {
        McpClient client = clients.remove(serverName);
        if (client == null) return false;
        try { client.close(); } catch (Exception ignored) {}
        registry.unregisterByServer(serverName);
        System.out.println("[MCP] " + serverName + " disconnected, tools removed");
        return true;
    }

    public String refresh() throws IOException {
        AgentConfig.McpServerConfig[] latestServers = AgentConfig.loadMcpServers(
            java.nio.file.Paths.get(System.getProperty("user.home"), ".self-agent", "config.mcp.json")
        ).toArray(new AgentConfig.McpServerConfig[0]);

        int added = 0;
        for (AgentConfig.McpServerConfig serverCfg : latestServers) {
            if (!serverCfg.enabled) continue;
            if (!clients.containsKey(serverCfg.name)) {
                if (connect(serverCfg)) added++;
            }
        }
        // 更新 config
        config.mcpServers = List.of(latestServers);
        return "Refresh complete: " + added + " new server(s) connected";
    }

    private McpClient buildClient(AgentConfig.McpServerConfig cfg) throws IOException {
        String transport = cfg.transport != null ? cfg.transport.toLowerCase() : "stdio";
        return switch (transport) {
            case "streamable-http", "http" ->
                new McpClient(new StreamableHttpTransport(requireUrl(cfg)), cfg.name);
            default -> {
                if (cfg.command == null || cfg.command.isEmpty())
                    throw new IllegalArgumentException("MCP server '" + cfg.name + "' requires 'command' for stdio transport");
                yield new McpClient(new StdioTransport(cfg.command.toArray(new String[0])), cfg.name);
            }
        };
    }

    private String requireUrl(AgentConfig.McpServerConfig cfg) {
        if (cfg.url == null || cfg.url.isBlank())
            throw new IllegalArgumentException("MCP server '" + cfg.name + "' requires 'url' for transport: " + cfg.transport);
        return cfg.url;
    }

    public boolean isConnected(String serverName) { return clients.containsKey(serverName); }

    public void closeAll() {
        clients.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });
        clients.clear();
    }
}
