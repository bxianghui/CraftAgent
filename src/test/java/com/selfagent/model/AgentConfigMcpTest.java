package com.selfagent.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AgentConfigMcpTest {
    @Test
    void loadsMcpServersFromJsonFile(@TempDir Path tmp) throws IOException {
        Path cfg = tmp.resolve("config.yaml");
        Files.writeString(cfg, """
            default_provider: anthropic
            providers:
              anthropic:
                api_key: sk-test
                model: claude-sonnet-4-6
            """);
        Path mcpCfg = tmp.resolve("config.mcp.json");
        Files.writeString(mcpCfg, """
            [
              {
                "name": "filesystem",
                "command": ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
                "env": {}
              },
              {
                "name": "brave-search",
                "command": ["npx", "-y", "@modelcontextprotocol/server-brave-search"],
                "env": {"BRAVE_API_KEY": "test-key"}
              }
            ]
            """);
        AgentConfig config = AgentConfig.load(cfg);
        assertNotNull(config.mcpServers);
        assertEquals(2, config.mcpServers.size());
        assertEquals("filesystem", config.mcpServers.get(0).name);
        assertEquals(4, config.mcpServers.get(0).command.size());
        assertEquals("brave-search", config.mcpServers.get(1).name);
        assertEquals("test-key", config.mcpServers.get(1).env.get("BRAVE_API_KEY"));
    }

    @Test
    void emptyMcpServersWhenJsonFileMissing(@TempDir Path tmp) throws IOException {
        Path cfg = tmp.resolve("config.yaml");
        Files.writeString(cfg, """
            default_provider: anthropic
            providers:
              anthropic:
                api_key: sk-test
                model: claude-sonnet-4-6
            """);
        AgentConfig config = AgentConfig.load(cfg);
        assertTrue(config.mcpServers == null || config.mcpServers.isEmpty());
    }
}
