package com.selfagent.agent;

import com.selfagent.model.ToolCall;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ContextManagerTest {
    @Test
    void buildsMessagesWithHistory() {
        ContextManager cm = new ContextManager(100_000, 0.8, 20);
        cm.addUserMessage("Hello");
        cm.addAssistantMessage("Hi!", List.of());
        List<Map<String, Object>> msgs = cm.buildMessages();
        assertEquals(2, msgs.size());
        assertEquals("user", msgs.get(0).get("role"));
        assertEquals("assistant", msgs.get(1).get("role"));
    }

    @Test
    void estimatesTokenCount() {
        ContextManager cm = new ContextManager(100_000, 0.8, 20);
        cm.addUserMessage("Hello world this is a test message");
        int tokens = cm.estimateTokens();
        assertTrue(tokens > 0 && tokens < 100);
    }

    @Test
    void clearsHistory() {
        ContextManager cm = new ContextManager(100_000, 0.8, 20);
        cm.addUserMessage("msg1");
        cm.addAssistantMessage("resp1", List.of());
        cm.clear();
        assertEquals(0, cm.buildMessages().size());
    }
}
