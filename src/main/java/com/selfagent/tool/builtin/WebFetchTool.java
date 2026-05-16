package com.selfagent.tool.builtin;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

public class WebFetchTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient http = new OkHttpClient();
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\n{3,}");

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("url").put("type", "string").put("description", "HTTP/HTTPS URL to fetch");
        schema.putArray("required").add("url");
        return new ToolDefinition("web_fetch",
            "Fetch URL content as plain text (HTML tags removed). Returns up to 8000 characters. " +
            "Use for fetching documentation, README files, API responses, or any web content. " +
            "Content is truncated if larger than 8000 chars.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String url = (String) params.get("url");
        try {
            Request req = new Request.Builder().url(url)
                .addHeader("User-Agent", "coding-agent/1.0").build();
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) return ToolResult.error("HTTP " + resp.code());
                String body = resp.body().string();
                String text = HTML_TAGS.matcher(body).replaceAll(" ");
                text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n").trim();
                if (text.length() > 8000) text = text.substring(0, 8000) + "\n...(truncated)";
                return ToolResult.ok(text);
            }
        } catch (IOException e) {
            return ToolResult.error("Fetch failed: " + e.getMessage());
        }
    }
}
