package com.selfagent.skill;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionTrackerTest {
    @Test
    void countsToolCalls() {
        SessionTracker t = new SessionTracker();
        t.onToolCall("bash");
        t.onToolCall("edit_file");
        t.onToolCall("bash");
        assertEquals(3, t.getTotalToolCallCount());
    }

    @Test
    void detectsSelfcheckPass() {
        SessionTracker t = new SessionTracker();
        assertFalse(t.isSelfcheckPassed());
        t.onSelfcheckPass();
        assertTrue(t.isSelfcheckPassed());
    }

    @Test
    void detectsConfirmWords() {
        SessionTracker t = new SessionTracker();
        t.onUserMessage("好了，问题解决了");
        assertTrue(t.hasConfirmWord());
    }

    @Test
    void noConfirmWordForRegularMessage() {
        SessionTracker t = new SessionTracker();
        t.onUserMessage("帮我看下这个文件");
        assertFalse(t.hasConfirmWord());
    }

    @Test
    void shouldExtractWhenSelfcheckPassAndEnoughToolCalls() {
        SessionTracker t = new SessionTracker();
        t.onToolCall("bash"); t.onToolCall("bash"); t.onToolCall("bash");
        t.onSelfcheckPass();
        assertTrue(t.shouldExtract());
    }

    @Test
    void shouldNotExtractWithFewerThanThreeToolCalls() {
        SessionTracker t = new SessionTracker();
        t.onToolCall("bash"); t.onToolCall("bash");
        t.onSelfcheckPass();
        assertFalse(t.shouldExtract());
    }

    @Test
    void shouldExtractWithConfirmWordAndEnoughToolCalls() {
        SessionTracker t = new SessionTracker();
        t.onToolCall("bash"); t.onToolCall("edit_file"); t.onToolCall("bash");
        t.onUserMessage("谢谢，解决了");
        assertTrue(t.shouldExtract());
    }
}
