package com.selfagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PersistentMemoryTest {
    @Test
    void savesAndLoadsEntry(@TempDir Path tmp) throws IOException {
        PersistentMemory mem = new PersistentMemory(tmp);
        MemoryEntry entry = new MemoryEntry("user_profile", "Java dev with 5yr experience",
            "user", "User has 5 years Java experience.");
        mem.save(entry);

        List<MemoryEntry> index = mem.loadIndex();
        assertEquals(1, index.size());
        assertEquals("user_profile", index.get(0).name);
        assertEquals("Java dev with 5yr experience", index.get(0).description);
    }

    @Test
    void searchByKeyword(@TempDir Path tmp) throws IOException {
        PersistentMemory mem = new PersistentMemory(tmp);
        mem.save(new MemoryEntry("java_exp", "Java developer experience", "user", "5 years Java."));
        mem.save(new MemoryEntry("mcp_knowledge", "MCP protocol expertise", "project", "Knows MCP stdio."));

        List<MemoryEntry> results = mem.search("java");
        assertEquals(1, results.size());
        assertEquals("java_exp", results.get(0).name);
    }

    @Test
    void deleteByKeyword(@TempDir Path tmp) throws IOException {
        PersistentMemory mem = new PersistentMemory(tmp);
        mem.save(new MemoryEntry("old_task", "completed task info", "task", "Task done."));
        mem.delete("old_task");

        List<MemoryEntry> index = mem.loadIndex();
        assertTrue(index.isEmpty());
    }

    @Test
    void loadsEntryContent(@TempDir Path tmp) throws IOException {
        PersistentMemory mem = new PersistentMemory(tmp);
        mem.save(new MemoryEntry("pref", "output preference", "feedback", "User likes concise output."));

        MemoryEntry loaded = mem.loadEntry("pref.md");
        assertNotNull(loaded);
        assertEquals("User likes concise output.", loaded.content);
    }
}
