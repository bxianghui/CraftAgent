package com.selfagent.selfcheck;

public class VerificationTrigger {
    private static final int SUGGEST_THRESHOLD = 3;
    private int toolCallCount = 0;
    private boolean suggested = false;

    public boolean increment() {
        toolCallCount++;
        if (toolCallCount >= SUGGEST_THRESHOLD && !suggested) {
            suggested = true;
            return true;
        }
        return false;
    }

    public void reset() {
        toolCallCount = 0;
        suggested = false;
    }

    public int getCount() { return toolCallCount; }
}
