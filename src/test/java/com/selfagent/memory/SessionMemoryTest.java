package com.selfagent.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryTest {
    @Test
    void storesAndRetrievesEntries() {
        SessionMemory mem = new SessionMemory();
        mem.put("current_task", "implement memory module");
        mem.put("user_preference", "prefers concise output");
        assertEquals("implement memory module", mem.getAll().get("current_task"));
        assertEquals(2, mem.getAll().size());
    }

    @Test
    void toSystemPromptBlock() {
        SessionMemory mem = new SessionMemory();
        mem.put("task", "write tests");
        String block = mem.toSystemPromptBlock();
        assertTrue(block.contains("task"));
        assertTrue(block.contains("write tests"));
    }

    @Test
    void emptyReturnsEmptyBlock() {
        SessionMemory mem = new SessionMemory();
        assertEquals("", mem.toSystemPromptBlock());
    }
}
