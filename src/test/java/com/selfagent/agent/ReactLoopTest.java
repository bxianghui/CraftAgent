package com.selfagent.agent;

import com.selfagent.model.*;
import com.selfagent.tool.*;
import com.selfagent.tool.builtin.BashTool;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ReactLoopTest {
    @Test
    void returnsDirectAnswerWithNoToolCalls() throws Exception {
        LLMProvider stubProvider = new LLMProvider() {
            @Override public ChatResponse chat(ChatRequest req) {
                return new ChatResponse("42 is the answer", null, List.of(), 10, 5);
            }
            @Override public Stream<ChatChunk> stream(ChatRequest req) { throw new UnsupportedOperationException(); }
            @Override public boolean supportsNativeToolUse() { return true; }
            @Override public int maxTokens() { return 100_000; }
        };

        ToolRegistry registry = new ToolRegistry();
        ContextManager cm = new ContextManager(100_000, 0.8, 20);
        AgentContext ctx = new AgentContext(stubProvider, Path.of(System.getProperty("java.io.tmpdir")), false, null);
        ReactLoop loop = new ReactLoop(registry, cm);

        String result = loop.run("what is the answer?", ctx);
        assertEquals("42 is the answer", result);
    }

    @Test
    void executesOneToolCallThenReturns() throws Exception {
        List<ChatRequest> calls = new ArrayList<>();
        LLMProvider stubProvider = new LLMProvider() {
            int callCount = 0;
            @Override public ChatResponse chat(ChatRequest req) {
                calls.add(req);
                callCount++;
                if (callCount == 1) {
                    return new ChatResponse(null, null,
                        List.of(new ToolCall("tc_1", "bash", Map.of("cmd", "echo hi"))), 10, 5);
                }
                return new ChatResponse("Done", null, List.of(), 10, 5);
            }
            @Override public Stream<ChatChunk> stream(ChatRequest req) { throw new UnsupportedOperationException(); }
            @Override public boolean supportsNativeToolUse() { return true; }
            @Override public int maxTokens() { return 100_000; }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(new BashTool());
        ContextManager cm = new ContextManager(100_000, 0.8, 20);
        AgentContext ctx = new AgentContext(stubProvider, Path.of(System.getProperty("java.io.tmpdir")), true, null);
        ReactLoop loop = new ReactLoop(registry, cm);

        String result = loop.run("run echo hi", ctx);
        assertEquals("Done", result);
        assertEquals(2, calls.size());
    }
}
