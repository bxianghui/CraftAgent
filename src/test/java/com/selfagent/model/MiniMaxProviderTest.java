package com.selfagent.model;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MiniMaxProviderTest {
    MockWebServer server;
    MiniMaxProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new MiniMaxProvider("sk-cp-QCfYmDJI3wiGcaGINzXoQQY39O5-Snn1Ke24YptmRqasLppGJHxFaF5AVX-E02iiJ3e7wwNdOCFeUiTxwi1hqb9n5QLZh9tuEVQLN6YvWASBjG4hEF1zveI", "MiniMax-M2.7",
            "https://api.minimaxi.com/anthropic/", 0.8f);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void parsesTextResponseIgnoringThinkingBlock() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {
              "id": "06379dbe27b33d7c58d8410a8efe6394",
              "type": "message",
              "role": "assistant",
              "model": "MiniMax-M2.7",
              "content": [
                {
                  "thinking": "用户用中文说\\"你好\\"，这是一个简单的问候。",
                  "signature": "ce704495524bad054531fe187e18b4a8d874a52fbb3923ce18fceace5e768ec9",
                  "type": "thinking"
                },
                {
                  "text": "你好！有什么我可以帮助你的吗？",
                  "type": "text"
                }
              ],
              "usage": {
                "input_tokens": 42,
                "output_tokens": 30
              },
              "stop_reason": "end_turn",
              "base_resp": {
                "status_code": 0,
                "status_msg": ""
              }
            }
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "你好")),
            List.of(), "MiniMax-M2.7", false);
        ChatResponse resp = provider.chat(req);

        assertEquals("你好！有什么我可以帮助你的吗？", resp.content);
        assertTrue(resp.toolCalls.isEmpty());
        assertEquals(42, resp.inputTokens);
        assertEquals(30, resp.outputTokens);
    }

    @Test
    void sendsCorrectRequestHeaders() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"1","type":"message","role":"assistant","model":"MiniMax-M2.7",
             "content":[{"type":"text","text":"ok"}],
             "usage":{"input_tokens":5,"output_tokens":2}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "hi")),
            List.of(), "MiniMax-M2.7", false);
        provider.chat(req);

        RecordedRequest recorded = server.takeRequest();
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"));
        assertTrue(recorded.getPath().endsWith("/v1/messages"));
    }

    @Test
    void sendsCorrectRequestBody() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"1","type":"message","role":"assistant","model":"MiniMax-M2.7",
             "content":[{"type":"text","text":"ok"}],
             "usage":{"input_tokens":5,"output_tokens":2}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "hi")),
            List.of(), "MiniMax-M2.7", false);
        provider.chat(req);

        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("MiniMax-M2.7"));
        assertTrue(body.contains("temperature"));
        assertTrue(body.contains("\"stream\":false"));
    }

    @Test
    void multipleTextBlocksAreConcatenated() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"1","type":"message","role":"assistant","model":"MiniMax-M2.7",
             "content":[
               {"type":"text","text":"第一段"},
               {"type":"text","text":"第二段"}
             ],
             "usage":{"input_tokens":5,"output_tokens":10}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "test")),
            List.of(), "MiniMax-M2.7", false);
        ChatResponse resp = provider.chat(req);

        assertTrue(resp.content.contains("第一段"));
        assertTrue(resp.content.contains("第二段"));
    }
}
