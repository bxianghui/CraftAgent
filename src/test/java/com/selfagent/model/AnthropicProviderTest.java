package com.selfagent.model;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class AnthropicProviderTest {
    MockWebServer server;
    AnthropicProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new AnthropicProvider("test-key", "claude-sonnet-4-6",
            server.url("/").toString(), 0.8f);
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void parsesTextResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"msg_1","type":"message","role":"assistant",
             "content":[{"type":"text","text":"Hello!"}],
             "usage":{"input_tokens":10,"output_tokens":5}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "Hi")),
            List.of(), "claude-sonnet-4-6", false);
        ChatResponse resp = provider.chat(req);

        assertEquals("Hello!", resp.content);
        assertTrue(resp.toolCalls.isEmpty());
        assertEquals(10, resp.inputTokens);
        assertEquals(5, resp.outputTokens);
    }

    @Test
    void parsesToolUseResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("""
            {"id":"msg_2","type":"message","role":"assistant",
             "content":[{"type":"tool_use","id":"tu_1","name":"read_file",
               "input":{"path":"/tmp/test.txt"}}],
             "usage":{"input_tokens":20,"output_tokens":15}}
            """).addHeader("Content-Type", "application/json"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "read file")),
            List.of(), "claude-sonnet-4-6", false);
        ChatResponse resp = provider.chat(req);

        assertTrue(resp.hasToolCalls());
        assertEquals("read_file", resp.toolCalls.get(0).name);
        assertEquals("/tmp/test.txt", resp.toolCalls.get(0).arguments.get("path"));
    }

    @Test
    void streamingToolCallArgsCorrectlyMappedViaIndex() throws Exception {
        // 验证流式 tool call 参数通过 index→id 正确拼接
        // Anthropic SSE: content_block_start 用 id，input_json_delta 用 index
        String sseBody =
            "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"bash\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"cmd\\\"\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\": \\\"ls\\\"}\" }}\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n";

        server.enqueue(new MockResponse()
            .setBody(sseBody)
            .addHeader("Content-Type", "text/event-stream"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "list files")),
            List.of(), "claude-sonnet-4-6", true);

        List<ChatChunk> chunks = provider.stream(req).collect(Collectors.toList());

        // 找到 toolCallStart 和 toolCallArgs
        ChatChunk start = chunks.stream()
            .filter(c -> "toolu_01".equals(c.toolCallId) && "bash".equals(c.toolCallName))
            .findFirst().orElse(null);
        assertNotNull(start, "should have toolCallStart with id=toolu_01");

        // args 应该归到 toolu_01，而不是 index "1"
        String allArgs = chunks.stream()
            .filter(c -> "toolu_01".equals(c.toolCallId) && c.toolCallArgsDelta != null)
            .map(c -> c.toolCallArgsDelta)
            .collect(Collectors.joining());
        assertTrue(allArgs.contains("cmd"), "args should contain 'cmd', got: " + allArgs);
        assertTrue(allArgs.contains("ls"), "args should contain 'ls', got: " + allArgs);
    }

    @Test
    void streamingToolCallArgsNullWhenIndexMismatch_regression() throws Exception {
        // 回归测试：修复前 index!=id 导致 args 为空，工具参数全是 null
        // 这个测试确保不会退化回旧行为
        String sseBody =
            "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_abc\",\"name\":\"read_file\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"/tmp/test.txt\\\"}\"}}\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n";

        server.enqueue(new MockResponse()
            .setBody(sseBody)
            .addHeader("Content-Type", "text/event-stream"));

        ChatRequest req = new ChatRequest(
            List.of(Map.of("role", "user", "content", "read file")),
            List.of(), "claude-sonnet-4-6", true);

        List<ChatChunk> chunks = provider.stream(req).collect(Collectors.toList());

        // args 必须用 toolu_abc 而不是 "0"
        long argsWithCorrectId = chunks.stream()
            .filter(c -> "toolu_abc".equals(c.toolCallId) && c.toolCallArgsDelta != null)
            .count();
        assertTrue(argsWithCorrectId > 0, "args should be keyed by tool id 'toolu_abc', not index '0'");

        // 不应有以 "0" 为 id 的 args（旧行为的 bug）
        long argsWithWrongId = chunks.stream()
            .filter(c -> "0".equals(c.toolCallId) && c.toolCallArgsDelta != null)
            .count();
        assertEquals(0, argsWithWrongId, "args should NOT be keyed by index string '0'");
    }
}
