package com.selfagent.tool;

import com.selfagent.model.AgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class McpManagerTest {

    private AgentConfig.McpServerConfig mockServerConfig(Path tmp) throws IOException {
        File script = tmp.resolve("mock_mcp.sh").toFile();
        String content = """
            #!/bin/bash
            while IFS= read -r line; do
              method=$(echo "$line" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('method',''))" 2>/dev/null)
              id=$(echo "$line" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',1))" 2>/dev/null)
              if [ "$method" = "initialize" ]; then
                echo '{"jsonrpc":"2.0","id":'$id',"result":{"protocolVersion":"2024-11-05","capabilities":{}}}'
              elif [ "$method" = "notifications/initialized" ]; then
                :
              elif [ "$method" = "tools/list" ]; then
                echo '{"jsonrpc":"2.0","id":'$id',"result":{"tools":[{"name":"mock_tool","description":"A mock tool","inputSchema":{"type":"object","properties":{}}}]}}'
              fi
            done
            """;
        try (var w = new FileWriter(script)) { w.write(content); }
        script.setExecutable(true);
        AgentConfig.McpServerConfig cfg = new AgentConfig.McpServerConfig();
        cfg.name = "mock";
        cfg.transport = "stdio";
        cfg.command = List.of("bash", script.getAbsolutePath());
        cfg.env = Map.of();
        return cfg;
    }

    @Test
    void registersToolsFromMcpServer(@TempDir Path tmp) throws Exception {
        AgentConfig.McpServerConfig serverCfg = mockServerConfig(tmp);
        AgentConfig config = new AgentConfig();
        config.mcpServers = List.of(serverCfg);

        ToolRegistry registry = new ToolRegistry();
        McpManager manager = new McpManager(config, registry);
        manager.registerAll();

        assertTrue(registry.has("mock_tool"));
        manager.closeAll();
    }

    @Test
    void emptyMcpServersDoesNothing() throws Exception {
        AgentConfig config = new AgentConfig();
        config.mcpServers = List.of();
        ToolRegistry registry = new ToolRegistry();
        McpManager manager = new McpManager(config, registry);
        manager.registerAll();
        assertEquals(0, registry.getDefinitions().size());
        manager.closeAll();
    }

    @Test
    void nullMcpServersDoesNothing() throws Exception {
        AgentConfig config = new AgentConfig();
        config.mcpServers = null;
        ToolRegistry registry = new ToolRegistry();
        McpManager manager = new McpManager(config, registry);
        manager.registerAll();
        assertEquals(0, registry.getDefinitions().size());
        manager.closeAll();
    }
}
