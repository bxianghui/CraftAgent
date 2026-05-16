package com.selfagent.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SessionTracker {
    private static final int MIN_TOOL_CALLS = 3;
    private static final Set<String> CONFIRM_WORDS = Set.of(
        "好了", "解决了", "成功了", "谢谢", "thank", "done", "ok", "fixed",
        "完成了", "可以了", "没问题", "正常了", "生效了", "works"
    );

    private int totalToolCallCount = 0;
    private boolean selfcheckPassed = false;
    private final List<String> recentUserMessages = new ArrayList<>();

    public void onToolCall(String toolName) {
        totalToolCallCount++;
    }

    public void onSelfcheckPass() {
        selfcheckPassed = true;
    }

    public void onUserMessage(String msg) {
        if (msg == null) return;
        recentUserMessages.add(msg);
        if (recentUserMessages.size() > 10) recentUserMessages.remove(0);
    }

    public int getTotalToolCallCount() { return totalToolCallCount; }
    public boolean isSelfcheckPassed() { return selfcheckPassed; }
    public List<String> getRecentUserMessages() {
        return Collections.unmodifiableList(recentUserMessages);
    }

    public boolean hasConfirmWord() {
        return recentUserMessages.stream().anyMatch(msg -> {
            String lower = msg.toLowerCase();
            return CONFIRM_WORDS.stream().anyMatch(lower::contains);
        });
    }

    public boolean shouldExtract() {
        if (totalToolCallCount < MIN_TOOL_CALLS) return false;
        return selfcheckPassed || hasConfirmWord();
    }
}
