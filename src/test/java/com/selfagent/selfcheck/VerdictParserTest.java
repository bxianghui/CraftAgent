package com.selfagent.selfcheck;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerdictParserTest {
    @Test
    void parsesPass() {
        assertEquals(VerdictParser.Verdict.PASS,
            VerdictParser.parse("All checks passed.\n\nVERDICT: PASS"));
    }

    @Test
    void parsesFail() {
        assertEquals(VerdictParser.Verdict.FAIL,
            VerdictParser.parse("Test failed.\nVERDICT: FAIL"));
    }

    @Test
    void parsesPartial() {
        assertEquals(VerdictParser.Verdict.PARTIAL,
            VerdictParser.parse("Cannot run tests.\nVERDICT: PARTIAL"));
    }

    @Test
    void returnsUnknownForNoVerdict() {
        assertEquals(VerdictParser.Verdict.UNKNOWN,
            VerdictParser.parse("Some output without verdict"));
    }

    @Test
    void returnsUnknownForNull() {
        assertEquals(VerdictParser.Verdict.UNKNOWN, VerdictParser.parse(null));
    }

    @Test
    void formatTaskNotificationContainsVerdict() {
        String notification = VerdictParser.formatTaskNotification("details", VerdictParser.Verdict.FAIL);
        assertTrue(notification.contains("FAIL"));
        assertTrue(notification.contains("task-notification"));
        assertTrue(notification.contains("Verification FAILED"));
    }
}
