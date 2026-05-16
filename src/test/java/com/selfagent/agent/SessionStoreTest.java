package com.selfagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    @Test
    void appendsAndListsSessions(@TempDir Path tmp) throws Exception {
        SessionStore store = new SessionStore(tmp);
        store.append("sess-1", "thought", "Hello world");
        store.append("sess-1", "action", "{\"tool\":\"bash\"}");
        store.append("sess-1", "observation", "output");
        List<String> lines = store.readSession("sess-1");
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("thought"));
        assertTrue(lines.get(1).contains("bash"));
    }

    @Test
    void listsSessions(@TempDir Path tmp) throws Exception {
        SessionStore store = new SessionStore(tmp);
        store.append("sess-a", "thought", "t1");
        store.append("sess-b", "thought", "t2");
        List<String> sessions = store.listSessions();
        assertTrue(sessions.contains("sess-a"));
        assertTrue(sessions.contains("sess-b"));
    }
}
