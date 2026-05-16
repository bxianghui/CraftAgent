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

class OpenAIProviderTest {
    MockWebServer server;
    OpenAIProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new OpenAIProvider("test-key", "gpt-4o", server.url("/").toString(), 0.8f);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void parsesTextResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"chatcmpl-1","choices":[{"message":{"role":"assistant",
             "content":"Hello!","tool_calls":null},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":5}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "Hi")),
            List.of(), "gpt-4o", false);
        ChatResponse resp = provider.chat(req);

        assertEquals("Hello!", resp.content);
        assertTrue(resp.toolCalls.isEmpty());
    }

    @Test
    void parsesToolCallsResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"chatcmpl-2","choices":[{"message":{"role":"assistant","content":null,
             "tool_calls":[{"id":"call_1","type":"function",
               "function":{"name":"bash","arguments":"{\\"cmd\\":\\"ls\\"}"}}]},
             "finish_reason":"tool_calls"}],
             "usage":{"prompt_tokens":20,"completion_tokens":10}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "list files")),
            List.of(), "gpt-4o", false);
        ChatResponse resp = provider.chat(req);

        assertTrue(resp.hasToolCalls());
        assertEquals("bash", resp.toolCalls.get(0).name);
        assertEquals("ls", resp.toolCalls.get(0).arguments.get("cmd"));
    }
}
