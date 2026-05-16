package com.selfagent.model;

public class ChatChunk {
    public final String deltaContent;
    public final String thinkingDelta;
    public final String toolCallId;
    public final String toolCallName;
    public final String toolCallArgsDelta;
    public final boolean done;

    public ChatChunk(String deltaContent, String thinkingDelta, String toolCallId,
                     String toolCallName, String toolCallArgsDelta, boolean done) {
        this.deltaContent = deltaContent;
        this.thinkingDelta = thinkingDelta;
        this.toolCallId = toolCallId;
        this.toolCallName = toolCallName;
        this.toolCallArgsDelta = toolCallArgsDelta;
        this.done = done;
    }

    public static ChatChunk text(String delta) {
        return new ChatChunk(delta, null, null, null, null, false);
    }

    public static ChatChunk thinking(String delta) {
        return new ChatChunk(null, delta, null, null, null, false);
    }

    public static ChatChunk toolCallStart(String id, String name) {
        return new ChatChunk(null, null, id, name, null, false);
    }

    public static ChatChunk toolCallArgs(String id, String argsDelta) {
        return new ChatChunk(null, null, id, null, argsDelta, false);
    }

    public static ChatChunk done() {
        return new ChatChunk(null, null, null, null, null, true);
    }
}
