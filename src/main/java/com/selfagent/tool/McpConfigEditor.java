package com.selfagent.tool;

import com.selfagent.model.AgentConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class McpConfigEditor {
    private static final Path MCP_CONFIG =
        Paths.get(System.getProperty("user.home"), ".self-agent", "config.mcp.json");
    private static final ObjectMapper mapper =
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static boolean setEnabled(String serverName, boolean enabled) throws IOException {
        if (!Files.exists(MCP_CONFIG)) return false;
        List<AgentConfig.McpServerConfig> servers = mapper.readValue(
            MCP_CONFIG.toFile(), new TypeReference<>() {});
        boolean found = false;
        for (AgentConfig.McpServerConfig s : servers) {
            if (serverName.equals(s.name)) {
                s.enabled = enabled;
                found = true;
                break;
            }
        }
        if (found) {
            mapper.writeValue(MCP_CONFIG.toFile(), servers);
        }
        return found;
    }
}
