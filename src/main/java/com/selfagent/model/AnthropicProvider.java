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

@ModelProvider(name = "anthropic", description = "Anthropic Provider")
public class AnthropicProvider implements LLMProvider {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final int MAX_TOKENS = 200_000;

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

    public AnthropicProvider(String apiKey, String model, String baseUrl, float temperature) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.temperature = temperature;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            Request httpReq = new Request.Builder()
                .url(baseUrl + "v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            try (Response resp = http.newCall(httpReq).execute()) {
                String json = resp.body().string();
                return parseResponse(mapper.readTree(json));
            }
        } catch (IOException e) {
            throw new RuntimeException("Anthropic API error", e);
        }
    }

    @Override
    public Stream<ChatChunk> stream(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            body.put("stream", true);
            Request httpReq = new Request.Builder()
                .url(baseUrl + "v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            Response resp = http.newCall(httpReq).execute();
            java.util.stream.Stream.Builder<ChatChunk> builder = java.util.stream.Stream.builder();
            // index → id 映射：Anthropic 用 index 标识 delta，用 id 标识 tool_use block，需要对应
            java.util.Map<Integer, String> indexToId = new java.util.HashMap<>();
            SseReader.read(resp.body().byteStream(), data -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    String type = node.path("type").asText();
                    if ("content_block_delta".equals(type)) {
                        JsonNode delta = node.path("delta");
                        String deltaType = delta.path("type").asText();
                        if ("text_delta".equals(deltaType)) {
                            builder.accept(ChatChunk.text(delta.path("text").asText()));
                        } else if ("thinking_delta".equals(deltaType)) {
                            builder.accept(ChatChunk.thinking(delta.path("thinking").asText()));
                        } else if ("input_json_delta".equals(deltaType)) {
                            int index = node.path("index").asInt();
                            // 用 index 查找对应的 tool id，保证 args 归到正确的 tool
                            String toolId = indexToId.getOrDefault(index, String.valueOf(index));
                            builder.accept(ChatChunk.toolCallArgs(toolId,
                                delta.path("partial_json").asText()));
                        }
                    } else if ("content_block_start".equals(type)) {
                        int index = node.path("index").asInt();
                        JsonNode block = node.path("content_block");
                        if ("tool_use".equals(block.path("type").asText())) {
                            String id = block.path("id").asText();
                            String name = block.path("name").asText();
                            indexToId.put(index, id);
                            builder.accept(ChatChunk.toolCallStart(id, name));
                        }
                    } else if ("message_stop".equals(type)) {
                        builder.accept(ChatChunk.done());
                    }
                } catch (Exception ignored) {}
            });
            builder.accept(ChatChunk.done());
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Anthropic stream error", e);
        }
    }

    @Override public boolean supportsNativeToolUse() { return true; }
    @Override public int maxTokens() { return MAX_TOKENS; }

    private ObjectNode buildBody(ChatRequest req) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", req.model != null ? req.model : model);
        body.put("max_tokens", 4096);
        body.put("stream", false);
        body.put("temperature", temperature);
        if (req.systemPrompt != null && !req.systemPrompt.isBlank()) {
            body.put("system", req.systemPrompt);
        }
        ArrayNode msgs = body.putArray("messages");
        for (Map<String, Object> m : req.messages) {
            msgs.add(mapper.valueToTree(m));
        }
        if (req.tools != null && !req.tools.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            req.tools.forEach(tools::add);
        }
        return body;
    }

    private ChatResponse parseResponse(JsonNode root) {
        String content = null;
        String thinkingContent = null;
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode contentArr = root.get("content");
        if (contentArr != null) {
            for (JsonNode block : contentArr) {
                String type = block.get("type").asText();
                if ("text".equals(type)) {
                    content = block.get("text").asText();
                } else if ("thinking".equals(type)) {
                    thinkingContent = block.path("thinking").asText();
                } else if ("tool_use".equals(type)) {
                    String id = block.get("id").asText();
                    String name = block.get("name").asText();
                    Map<String, Object> args = mapper.convertValue(block.get("input"), Map.class);
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }
        }
        int inputTokens = 0, outputTokens = 0;
        JsonNode usage = root.get("usage");
        if (usage != null) {
            inputTokens = usage.path("input_tokens").asInt();
            outputTokens = usage.path("output_tokens").asInt();
        }
        return new ChatResponse(content, thinkingContent, toolCalls, inputTokens, outputTokens);
    }
}
