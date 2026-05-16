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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ModelProvider(name = "minimax", description = "MiniMax Provider")
public class MiniMaxProvider implements LLMProvider {
    private static final MediaType JSON = MediaType.get("application/json");
    public static final int MAX_TOKENS = 204_800;

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

    public MiniMaxProvider(String apiKey, String model, String baseUrl, float temperature) {
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
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                    .build();
            try (Response resp = http.newCall(httpReq).execute()) {
                return parseResponse(mapper.readTree(resp.body().string()));
            }
        } catch (IOException e) {
            throw new RuntimeException("miniMax API error", e);
        }
    }

    private ChatResponse parseResponse(JsonNode root) {
        String content = null;
        String thinkingContent = null;
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode contentArr = root.get("content");
        if (contentArr != null) {
            content = StreamSupport.stream(contentArr.spliterator(), false)
                    .filter(b -> "text".equals(b.get("type").asText()))
                    .map(b -> b.get("text").asText())
                    .collect(Collectors.joining("\n"));
            for (JsonNode block : contentArr) {
                String type = block.get("type").asText();
                if ("thinking".equals(type)) {
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

    private ObjectNode buildBody(ChatRequest req) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", req.model != null ? req.model : model);
        body.put("stream", false);
        body.put("temperature", temperature);
        if (req.systemPrompt != null && !req.systemPrompt.isBlank()) {
            body.put("system", req.systemPrompt);
        }
        ArrayNode msgs = body.putArray("messages");
        req.messages.forEach(m -> msgs.add(mapper.valueToTree(m)));
        if (req.tools != null && !req.tools.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            req.tools.forEach(tools::add);
        }
        return body;
    }

    @Override
    public Stream<ChatChunk> stream(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            body.put("stream", true);
            Request httpReq = new Request.Builder()
                    .url(baseUrl + "v1/messages")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                    .build();
            Response resp = http.newCall(httpReq).execute();
            java.util.stream.Stream.Builder<ChatChunk> builder = java.util.stream.Stream.builder();
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
            throw new RuntimeException("MiniMax stream error", e);
        }
    }

    @Override
    public boolean supportsNativeToolUse() {
        return true;
    }

    @Override
    public int maxTokens() {
        return MAX_TOKENS;
    }
}
