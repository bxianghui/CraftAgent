package com.selfagent.history;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class HistoryEvent {
    public final String ts;
    public final HistoryEventType type;
    public final String sessionId;
    public final Map<String, Object> payload;

    public HistoryEvent(HistoryEventType type, String sessionId, Map<String, Object> payload) {
        this.ts = Instant.now().toString();
        this.type = type;
        this.sessionId = sessionId;
        this.payload = payload;
    }

    public static HistoryEvent sessionStart(String sessionId) {
        return new HistoryEvent(HistoryEventType.SESSION_START, sessionId, Map.of());
    }

    public static HistoryEvent sessionEnd(String sessionId) {
        return new HistoryEvent(HistoryEventType.SESSION_END, sessionId, Map.of());
    }

    public static HistoryEvent userInput(String sessionId, String input) {
        return new HistoryEvent(HistoryEventType.USER_INPUT, sessionId, Map.of("input", input));
    }

    public static HistoryEvent thinking(String sessionId, String thinkingText) {
        return new HistoryEvent(HistoryEventType.THINKING, sessionId, Map.of("thinking", thinkingText));
    }

    public static HistoryEvent toolCall(String sessionId, String callId, String toolName, Map<String, Object> args) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callId", callId);
        payload.put("toolName", toolName);
        payload.put("arguments", args);
        return new HistoryEvent(HistoryEventType.TOOL_CALL, sessionId, payload);
    }

    public static HistoryEvent toolResult(String sessionId, String callId, String toolName,
                                          String content, boolean isError, long durationMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callId", callId);
        payload.put("toolName", toolName);
        payload.put("content", content);
        payload.put("isError", isError);
        payload.put("durationMs", durationMs);
        return new HistoryEvent(HistoryEventType.TOOL_RESULT, sessionId, payload);
    }

    public static HistoryEvent assistantText(String sessionId, String content) {
        return new HistoryEvent(HistoryEventType.ASSISTANT_TEXT, sessionId, Map.of("content", content));
    }
}
