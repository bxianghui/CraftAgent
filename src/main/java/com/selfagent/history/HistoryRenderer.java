package com.selfagent.history;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HistoryRenderer {
    public void render(List<HistoryEvent> events, PrintStream out) {
        out.println("─".repeat(60));
        AtomicInteger toolCalls = new AtomicInteger(0);
        AtomicLong totalMs = new AtomicLong(0);

        for (HistoryEvent e : events) {
            switch (e.type) {
                case SESSION_START -> out.println("Session: " + e.sessionId + "  " + e.ts);
                case USER_INPUT -> out.println("👤 User: " + e.payload.get("input"));
                case THINKING -> out.println("🤔 Thinking: " + truncate((String) e.payload.get("thinking"), 120));
                case TOOL_CALL -> {
                    toolCalls.incrementAndGet();
                    out.println("🔧 Tool: " + e.payload.get("toolName") + " " + e.payload.get("arguments"));
                }
                case TOOL_RESULT -> {
                    long ms = toLong(e.payload.get("durationMs"));
                    totalMs.addAndGet(ms);
                    boolean isError = Boolean.TRUE.equals(e.payload.get("isError"));
                    String status = isError ? "✗" : "✓";
                    String content = truncate((String) e.payload.get("content"), 80);
                    out.println("   └─ Result (" + ms + "ms): " + content + " " + status);
                }
                case ASSISTANT_TEXT -> out.println("💬 Assistant: " + truncate((String) e.payload.get("content"), 200));
                case SESSION_END -> {}
            }
        }
        out.println("─".repeat(60));
        out.println("Total: " + toolCalls.get() + " tool call(s), " + totalMs.get() + "ms");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }
}
