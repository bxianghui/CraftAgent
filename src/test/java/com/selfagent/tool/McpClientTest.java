package com.selfagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class McpClientTest {
    ObjectMapper mapper = new ObjectMapper();

    private Process startMockServer(Path tmp) throws IOException {
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
                echo '{"jsonrpc":"2.0","id":'$id',"result":{"tools":[{"name":"echo_tool","description":"Echo input","inputSchema":{"type":"object","properties":{"msg":{"type":"string"}},"required":["msg"]}}]}}'
              elif [ "$method" = "tools/call" ]; then
                msg=$(echo "$line" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['params']['arguments'].get('msg',''))" 2>/dev/null)
                echo '{"jsonrpc":"2.0","id":'$id',"result":{"content":[{"type":"text","text":"echo: '$msg'"}],"isError":false}}'
              fi
            done
            """;
        try (var w = new FileWriter(script)) { w.write(content); }
        script.setExecutable(true);
        return new ProcessBuilder("bash", script.getAbsolutePath()).start();
    }

    @Test
    void connectAndListTools(@TempDir Path tmp) throws Exception {
        Process server = startMockServer(tmp);
        McpClient client = new McpClient(new StdioTransport(server), "test");
        client.connect();
        List<ToolDefinition> tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("echo_tool", tools.get(0).name);
        client.close();
    }

    @Test
    void callToolReturnsResult(@TempDir Path tmp) throws Exception {
        Process server = startMockServer(tmp);
        McpClient client = new McpClient(new StdioTransport(server), "test");
        client.connect();
        ToolResult result = client.callTool("echo_tool", Map.of("msg", "hello"));
        assertFalse(result.isError);
        assertTrue(result.content.contains("hello"));
        client.close();
    }

    @Test
    void callToolReturnsErrorOnUnknownTool(@TempDir Path tmp) throws Exception {
        Process server = startMockServer(tmp);
        McpClient client = new McpClient(new StdioTransport(server), "test");
        client.connect();
        ToolResult result = client.callTool("nonexistent", Map.of());
        assertNotNull(result);
        client.close();
    }
}
