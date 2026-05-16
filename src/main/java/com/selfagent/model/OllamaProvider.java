package com.selfagent.model;

import com.selfagent.common.ModelProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import okhttp3.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

@ModelProvider(name = "ollama", description = "Ollama Provider")
public class OllamaProvider implements LLMProvider {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final int MAX_TOKENS = 32_000;

    private final String baseUrl;
    private final String model;
    public final float temperature;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaProvider(String baseUrl, String model, float temperature) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.model = model;
        this.temperature = temperature;
    }

    private ObjectNode buildBody(ChatRequest req) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", req.model != null ? req.model : model);
        body.put("temperature", temperature);
        ArrayNode msgs = body.putArray("messages");
        if (req.systemPrompt != null && !req.systemPrompt.isBlank()) {
            ObjectNode systemMsg = mapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", req.systemPrompt);
            msgs.add(systemMsg);
        }
        req.messages.forEach(m -> msgs.add(mapper.valueToTree(m)));
        return body;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            body.put("stream", false);

            Request httpReq = new Request.Builder()
                .url(baseUrl + "v1/chat/completions")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            try (Response resp = http.newCall(httpReq).execute()) {
                JsonNode root = mapper.readTree(resp.body().string());
                JsonNode msg = root.path("choices").get(0).path("message");
                String content = msg.path("content").asText();
                JsonNode usage = root.path("usage");
                return new ChatResponse(content, null, List.of(),
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt());
            }
        } catch (IOException e) {
            throw new RuntimeException("Ollama API error", e);
        }
    }

    @Override
    public Stream<ChatChunk> stream(ChatRequest request) {
        try {
            ObjectNode body = buildBody(request);
            body.put("stream", true);
            Request httpReq = new Request.Builder()
                .url(baseUrl + "v1/chat/completions")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            Response resp = http.newCall(httpReq).execute();
            java.util.stream.Stream.Builder<ChatChunk> builder = java.util.stream.Stream.builder();
            SseReader.read(resp.body().byteStream(), data -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta");
                    String content = delta.path("content").asText(null);
                    if (content != null && !content.isEmpty()) {
                        builder.accept(ChatChunk.text(content));
                    }
                    String finishReason = node.path("choices").path(0).path("finish_reason").asText(null);
                    if ("stop".equals(finishReason)) {
                        builder.accept(ChatChunk.done());
                    }
                } catch (Exception ignored) {}
            });
            builder.accept(ChatChunk.done());
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Ollama stream error", e);
        }
    }

    @Override public boolean supportsNativeToolUse() { return false; }
    @Override public int maxTokens() { return MAX_TOKENS; }
}
