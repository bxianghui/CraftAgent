package com.selfagent.sandbox;

import org.jline.reader.LineReader;
import java.io.PrintWriter;

public class ApprovalGate {
    private final boolean autoApprove;
    private final PrintWriter writer;
    private final LineReader lineReader;

    public ApprovalGate(boolean autoApprove, PrintWriter writer, LineReader lineReader) {
        this.autoApprove = autoApprove;
        this.writer = writer;
        this.lineReader = lineReader;
    }

    public boolean ask(String cmd) {
        if (autoApprove) return true;
        if (lineReader == null) return false; // 非交互模式无法审批，默认拒绝
        writer.println("\033[33m \n  ⚠️  Execute command? [y/N]\033[0m");
        writer.println("  $ " + cmd);
        writer.flush();
        try {
            String input = lineReader.readLine("> ");
            return input != null && input.trim().equalsIgnoreCase("y");
        } catch (Exception e) {
            return false;
        }
    }
}
