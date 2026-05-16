package com.selfagent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import java.io.IOException;

public class LLMEmbeddingService implements EmbeddingService {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient http = new OkHttpClient();
    private static final int DIMENSIONS = 1536;

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public LLMEmbeddingService(String baseUrl, String apiKey, String model) {
        String url = baseUrl != null ? baseUrl : "https://api.openai.com";
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.apiKey = apiKey;
        this.model = model != null ? model : "text-embedding-3-small";
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("input", text);
            Request req = new Request.Builder()
                .url(baseUrl + "/v1/embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
            try (Response resp = http.newCall(req).execute()) {
                String json = resp.body().string();
                JsonNode root = mapper.readTree(json);
                JsonNode embedding = root.path("data").get(0).path("embedding");
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = (float) embedding.get(i).asDouble();
                }
                return result;
            }
        } catch (IOException e) {
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimensions() { return DIMENSIONS; }
}
