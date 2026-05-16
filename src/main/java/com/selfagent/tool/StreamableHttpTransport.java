package com.selfagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

public class StreamableHttpTransport implements McpTransport {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String mcpEndpoint;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private String sessionId;

    public StreamableHttpTransport(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
        this.http = new OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public void connect() {
        // 无状态，session 在 initialize 握手后通过响应 header 建立
    }

    @Override
    public JsonNode send(String message) throws IOException {
        // 解析请求 id，用于从 SSE 流中匹配响应
        JsonNode reqNode = mapper.readTree(message);
        JsonNode idNode = reqNode.get("id");
        boolean isNotification = idNode == null; // notification 没有 id

        Request.Builder reqBuilder = new Request.Builder()
            .url(mcpEndpoint)
            .addHeader("Accept", "application/json, text/event-stream")
            .post(RequestBody.create(message, JSON));

        if (sessionId != null) {
            reqBuilder.addHeader("Mcp-Session-Id", sessionId);
        }

        try (Response resp = http.newCall(reqBuilder.build()).execute()) {
            String newSessionId = resp.header("Mcp-Session-Id");
            if (newSessionId != null) this.sessionId = newSessionId;

            if (resp.code() == 202) return null; // notification accepted, no body

            ResponseBody body = resp.body();
            if (body == null) return null;

            String contentType = resp.header("Content-Type", "");
            if (contentType != null && contentType.contains("text/event-stream")) {
                return parseSseResponse(body.byteStream(), isNotification ? null : idNode);
            } else {
                String json = body.string();
                return json.isBlank() ? null : mapper.readTree(json);
            }
        }
    }

    /**
     * 读完整 SSE 流，跳过 notification（无 id 或 id 不匹配），
     * 返回与请求 id 匹配的 response。
     */
    private JsonNode parseSseResponse(InputStream stream, JsonNode requestId) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder dataBuffer = new StringBuilder();
        JsonNode result = null;
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                dataBuffer.append(line.substring(5).trim());
            } else if (line.isEmpty() && dataBuffer.length() > 0) {
                String data = dataBuffer.toString();
                dataBuffer.setLength(0);
                try {
                    JsonNode msg = mapper.readTree(data);
                    if (requestId == null) {
                        // notification 发送，不需要匹配响应，取第一条
                        if (result == null) result = msg;
                    } else {
                        JsonNode msgId = msg.get("id");
                        if (msgId != null && msgId.equals(requestId)) {
                            result = msg;
                            // 找到匹配响应后继续读完流（服务端会关闭）
                        }
                        // 无 id 的是 notification，跳过
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    @Override
    public boolean isAsync() { return false; }

    @Override
    public void close() {
        if (sessionId == null) return;
        try {
            Request req = new Request.Builder()
                .url(mcpEndpoint)
                .addHeader("Mcp-Session-Id", sessionId)
                .delete()
                .build();
            http.newCall(req).execute().close();
        } catch (Exception ignored) {}
        sessionId = null;
    }

    public String getSessionId() { return sessionId; }
}
