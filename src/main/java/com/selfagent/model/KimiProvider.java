package com.selfagent.model;

import com.selfagent.common.ModelProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@ModelProvider(name = "kimi", description = "Kimi (Moonshot) Provider — OpenAI-compatible API")
public class KimiProvider implements LLMProvider {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1/";
    private static final int MAX_TOKENS = 128_000;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final float temperature;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public KimiProvider(String apiKey, String model, String baseUrl, float temperature) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "moonshot-v1-8k";
        String url = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
        this.baseUrl = url.endsWith("/") ? url : url + "/";
        this.temperature = temperature;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            Request httpReq = new Request.Builder()
                .url(baseUrl + "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            try (Response resp = http.newCall(httpReq).execute()) {
                return parseResponse(mapper.readTree(resp.body().string()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Kimi API error", e);
        }
    }

    @Override
    public Stream<ChatChunk> stream(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            body.put("stream", true);
            Request httpReq = new Request.Builder()
                .url(baseUrl + "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            Response resp = http.newCall(httpReq).execute();
            // index → id mapping，与 OpenAI 流式格式一致
            java.util.Map<Integer, String> indexToId = new java.util.HashMap<>();
            java.util.stream.Stream.Builder<ChatChunk> builder = java.util.stream.Stream.builder();
            SseReader.read(resp.body().byteStream(), data -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta");
                    String content = delta.path("content").asText(null);
                    if (content != null && !content.isEmpty()) {
                        builder.accept(ChatChunk.text(content));
                    }
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            int index = tc.path("index").asInt(-1);
                            String id = tc.path("id").asText(null);
                            String name = tc.path("function").path("name").asText(null);
                            String argsDelta = tc.path("function").path("arguments").asText(null);
                            if (id != null && !id.isBlank()) {
                                indexToId.put(index, id);
                            }
                            if (name != null && !name.isBlank()) {
                                String resolvedId = id != null ? id : indexToId.getOrDefault(index, String.valueOf(index));
                                builder.accept(ChatChunk.toolCallStart(resolvedId, name));
                            } else if (argsDelta != null && !argsDelta.isEmpty()) {
                                String resolvedId = indexToId.getOrDefault(index, String.valueOf(index));
                                builder.accept(ChatChunk.toolCallArgs(resolvedId, argsDelta));
                            }
                        }
                    }
                    String finishReason = node.path("choices").path(0).path("finish_reason").asText(null);
                    if ("stop".equals(finishReason) || "tool_calls".equals(finishReason)) {
                        builder.accept(ChatChunk.done());
                    }
                } catch (Exception ignored) {}
            });
            builder.accept(ChatChunk.done());
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Kimi stream error", e);
        }
    }

    @Override public boolean supportsNativeToolUse() { return true; }
    @Override public int maxTokens() { return MAX_TOKENS; }

    private ObjectNode buildBody(ChatRequest req) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", req.model != null ? req.model : model);
        body.put("stream", false);
        body.put("temperature", temperature);
        ArrayNode msgs = body.putArray("messages");
        if (req.systemPrompt != null && !req.systemPrompt.isBlank()) {
            ObjectNode systemMsg = mapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", req.systemPrompt);
            msgs.add(systemMsg);
        }
        req.messages.forEach(m -> msgs.add(mapper.valueToTree(m)));
        if (req.tools != null && !req.tools.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            req.tools.forEach(t -> {
                ObjectNode wrapped = mapper.createObjectNode();
                wrapped.put("type", "function");
                wrapped.set("function", t);
                tools.add(wrapped);
            });
        }
        return body;
    }

    private ChatResponse parseResponse(JsonNode root) {
        JsonNode msg = root.path("choices").get(0).path("message");
        String content = msg.path("content").isNull() ? null : msg.path("content").asText();
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode tcs = msg.path("tool_calls");
        if (tcs.isArray()) {
            for (JsonNode tc : tcs) {
                String id = tc.get("id").asText();
                String name = tc.path("function").get("name").asText();
                String argsJson = tc.path("function").get("arguments").asText();
                try {
                    Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                    toolCalls.add(new ToolCall(id, name, args));
                } catch (Exception e) {
                    toolCalls.add(new ToolCall(id, name, Map.of("_raw", argsJson)));
                }
            }
        }
        JsonNode usage = root.path("usage");
        return new ChatResponse(content, null, toolCalls,
            usage.path("prompt_tokens").asInt(),
            usage.path("completion_tokens").asInt());
    }
}
