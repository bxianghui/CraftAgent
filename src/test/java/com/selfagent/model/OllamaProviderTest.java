package com.selfagent.model;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OllamaProviderTest {
    MockWebServer server;
    OllamaProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new OllamaProvider(server.url("/").toString(), "qwen2.5-coder:7b", 0.8f);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void parsesTextResponseViaOpenAICompat() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"ollama-1","choices":[{"message":{"role":"assistant",
             "content":"Hi there!","tool_calls":null},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":5,"completion_tokens":3}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "Hi")),
            List.of(), "qwen2.5-coder:7b", false);
        ChatResponse resp = provider.chat(req);

        assertEquals("Hi there!", resp.content);
    }

    @Test
    void supportsNativeToolUseReturnsFalse() {
        assertFalse(provider.supportsNativeToolUse());
    }
}
