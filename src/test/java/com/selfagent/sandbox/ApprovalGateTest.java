package com.selfagent.sandbox;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

class ApprovalGateTest {

    @Test
    void autoApproveSkipsPrompt() {
        ApprovalGate gate = new ApprovalGate(true, new PrintWriter(new StringWriter()), null);
        assertTrue(gate.ask("echo hello"), "autoApprove=true should always return true");
    }

    @Test
    void nonInteractiveModeRejectsWhenLineReaderNull() {
        // 非交互模式（lineReader=null）默认拒绝，防止无人值守时执行危险命令
        ApprovalGate gate = new ApprovalGate(false, new PrintWriter(new StringWriter()), null);
        assertFalse(gate.ask("echo hello"), "No lineReader should default to reject");
    }

    @Test
    void autoApproveTrumpsNullLineReader() {
        // autoApprove=true 时即使没有 lineReader 也放行（--yes 模式）
        ApprovalGate gate = new ApprovalGate(true, new PrintWriter(new StringWriter()), null);
        assertTrue(gate.ask("curl http://example.com"));
    }
}
