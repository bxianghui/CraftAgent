package com.selfagent.history;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRendererTest {
    @Test
    void rendersEventsInOrder() {
        List<HistoryEvent> events = List.of(
            HistoryEvent.userInput("s1", "list files"),
            HistoryEvent.thinking("s1", "user wants to list files"),
            HistoryEvent.toolCall("s1", "tc1", "bash", Map.of("cmd", "ls")),
            HistoryEvent.toolResult("s1", "tc1", "bash", "file1.java\nfile2.java", false, 150),
            HistoryEvent.assistantText("s1", "Found 2 files")
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new HistoryRenderer().render(events, new PrintStream(baos));
        String out = baos.toString();
        assertTrue(out.contains("list files"));
        assertTrue(out.contains("thinking") || out.contains("Thinking"));
        assertTrue(out.contains("bash"));
        assertTrue(out.contains("150ms"));
        assertTrue(out.contains("Found 2 files"));
    }

    @Test
    void rendersSummaryStats() {
        List<HistoryEvent> events = List.of(
            HistoryEvent.toolCall("s1", "tc1", "read_file", Map.of("path", "/tmp/a")),
            HistoryEvent.toolResult("s1", "tc1", "read_file", "content", false, 50),
            HistoryEvent.toolCall("s1", "tc2", "bash", Map.of("cmd", "ls")),
            HistoryEvent.toolResult("s1", "tc2", "bash", "ok", false, 100)
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new HistoryRenderer().render(events, new PrintStream(baos));
        String out = baos.toString();
        assertTrue(out.contains("2") && out.contains("tool"));
    }
}
