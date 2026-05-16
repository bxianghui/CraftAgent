package com.selfagent.cli;

import java.io.*;
import java.util.List;
import java.util.regex.Pattern;

public class PermissionGuard {
    private static final List<Pattern> DANGER_PATTERNS = List.of(
        Pattern.compile("rm\\s+-rf"),
        Pattern.compile("git\\s+push\\s+--force"),
        Pattern.compile("dd\\s+if="),
        Pattern.compile("mkfs\\."),
        Pattern.compile(":\\s*\\(\\s*\\)\\s*\\{"),
        Pattern.compile("chmod\\s+777"),
        Pattern.compile("sudo\\s+rm")
    );

    private final boolean autoApprove;
    private final BufferedReader in;
    private final PrintStream out;

    public PermissionGuard(boolean autoApprove, InputStream in, PrintStream out) {
        this.autoApprove = autoApprove;
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = out;
    }

    public static boolean isDangerous(String cmd) {
        return DANGER_PATTERNS.stream().anyMatch(p -> p.matcher(cmd).find());
    }

    public boolean requestPermission(String operation, String description) {
        if (autoApprove) return true;
        return prompt(description + " [y/n] ");
    }

    public boolean requestDangerousPermission(String cmd) {
        out.println("[DANGER] " + cmd);
        if (autoApprove) {
            out.println("Auto-approve does not apply to dangerous operations. Blocked.");
            return false;
        }
        return prompt("This is a dangerous operation. Proceed? [y/n] ");
    }

    private boolean prompt(String message) {
        out.print(message);
        try {
            String line = in.readLine();
            return line != null && line.trim().equalsIgnoreCase("y");
        } catch (IOException e) {
            return false;
        }
    }
}
