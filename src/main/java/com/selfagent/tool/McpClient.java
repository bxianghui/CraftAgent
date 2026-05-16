package com.selfagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class McpClient {
    private final McpTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger idGen = new AtomicInteger(1);
    private String serverName;

    public McpClient(McpTransport transport, String serverName) {
        this.transport = transport;
        this.serverName = serverName;
    }

    public void connect() throws IOException {
        transport.connect();
        sendInitialize();
    }

    private void sendInitialize() throws IOException {
        int id = idGen.getAndIncrement();
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", "initialize");
        ObjectNode params = req.putObject("params");
        params.put("protocolVersion", "2025-03-26");
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "self-agent").put("version", "1.0");
        exchange(mapper.writeValueAsString(req));

        ObjectNode notif = mapper.createObjectNode();
        notif.put("jsonrpc", "2.0");
        notif.put("method", "notifications/initialized");
        transport.send(mapper.writeValueAsString(notif)); // no response expected
    }

    public List<ToolDefinition> listTools() throws IOException {
        int id = idGen.getAndIncrement();
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", "tools/list");
        JsonNode resp = exchange(mapper.writeValueAsString(req));
        List<ToolDefinition> defs = new ArrayList<>();
        if (resp == null) return defs;
        JsonNode tools = resp.path("result").path("tools");
        for (JsonNode t : tools) {
            ObjectNode schema = t.path("inputSchema").isObject()
                ? (ObjectNode) t.path("inputSchema")
                : mapper.createObjectNode();
            defs.add(new ToolDefinition(
                t.get("name").asText(),
                t.path("description").asText(""),
                schema));
        }
        return defs;
    }

    public ToolResult callTool(String name, Map<String, Object> arguments) throws IOException {
        int id = idGen.getAndIncrement();
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", "tools/call");
        ObjectNode params = req.putObject("params");
        params.put("name", name);
        params.set("arguments", mapper.valueToTree(arguments));
        JsonNode resp = exchange(mapper.writeValueAsString(req));
        if (resp == null) return ToolResult.error("[MCP:" + serverName + "] no response");
        if (resp.has("error")) {
            return ToolResult.error("[MCP:" + serverName + "] " + resp.path("error").path("message").asText("unknown error"));
        }
        JsonNode result = resp.path("result");
        if (result.path("isError").asBoolean(false)) {
            return ToolResult.error("[MCP:" + serverName + "] " + extractText(result.path("content")));
        }
        return ToolResult.ok(extractText(result.path("content")));
    }

    private JsonNode exchange(String message) throws IOException {
        JsonNode resp = transport.send(message);
        if (transport.isAsync()) resp = transport.receive();
        return resp;
    }

    private String extractText(JsonNode content) {
        if (!content.isArray()) return content.asText("");
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString();
    }

    public void close() { transport.close(); }
    public String getServerName() { return serverName; }
    public void setServerName(String name) { this.serverName = name; }
}
