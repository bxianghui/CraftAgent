package com.selfagent.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistoryStoreTest {
    @Test
    void appendsAndReadsEvents(@TempDir Path tmp) throws IOException {
        HistoryStore store = new HistoryStore(tmp);
        store.append(HistoryEvent.sessionStart("sess-1"));
        store.append(HistoryEvent.userInput("sess-1", "hello"));
        store.append(HistoryEvent.assistantText("sess-1", "hi there"));
        store.flush(); // wait for async writes

        List<HistoryEvent> events = store.readSession("sess-1");
        assertEquals(3, events.size());
        assertEquals(HistoryEventType.SESSION_START, events.get(0).type);
        assertEquals(HistoryEventType.USER_INPUT, events.get(1).type);
        assertEquals("hello", events.get(1).payload.get("input"));
        assertEquals(HistoryEventType.ASSISTANT_TEXT, events.get(2).type);
    }

    @Test
    void listsSessions(@TempDir Path tmp) throws IOException {
        HistoryStore store = new HistoryStore(tmp);
        store.append(HistoryEvent.sessionStart("sess-a"));
        store.append(HistoryEvent.sessionStart("sess-b"));
        store.flush(); // wait for async writes

        List<String> sessions = store.listSessions();
        assertTrue(sessions.contains("sess-a"));
        assertTrue(sessions.contains("sess-b"));
    }

    @Test
    void returnsEmptyForMissingSession(@TempDir Path tmp) throws IOException {
        HistoryStore store = new HistoryStore(tmp);
        List<HistoryEvent> events = store.readSession("nonexistent");
        assertTrue(events.isEmpty());
    }
}
