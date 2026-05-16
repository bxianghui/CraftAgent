package com.selfagent.selfcheck;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerificationTriggerTest {
    @Test
    void doesNotSuggestBeforeThreshold() {
        VerificationTrigger t = new VerificationTrigger();
        assertFalse(t.increment()); // 1
        assertFalse(t.increment()); // 2
    }

    @Test
    void suggestsAtThreshold() {
        VerificationTrigger t = new VerificationTrigger();
        t.increment(); // 1
        t.increment(); // 2
        assertTrue(t.increment()); // 3 → suggest
    }

    @Test
    void suggestsOnlyOnce() {
        VerificationTrigger t = new VerificationTrigger();
        t.increment(); t.increment(); t.increment();
        assertFalse(t.increment()); // 4
        assertFalse(t.increment()); // 5
    }

    @Test
    void resetAllowsSuggestAgain() {
        VerificationTrigger t = new VerificationTrigger();
        t.increment(); t.increment(); t.increment();
        t.reset();
        assertFalse(t.increment()); // 1 after reset
        assertFalse(t.increment()); // 2
        assertTrue(t.increment());  // 3 → suggest again
    }
}
