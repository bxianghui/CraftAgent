package com.selfagent.common;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * thinking 日志开关。持久化路径：~/.self-agent/thinking.enabled
 */
public class ThinkingLogger {
    private static final Path CONFIG_FILE =
        Paths.get(System.getProperty("user.home"), ".self-agent", "thinking.enabled");
    private static final AtomicBoolean enabled = new AtomicBoolean(loadFromDisk());

    public static boolean isEnabled() { return enabled.get(); }

    public static void setEnabled(boolean on) {
        enabled.set(on);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, String.valueOf(on));
        } catch (IOException e) {
            System.err.println("[ThinkingLogger] Failed to persist: " + e.getMessage());
        }
    }

    private static boolean loadFromDisk() {
        try {
            if (Files.exists(CONFIG_FILE))
                return Boolean.parseBoolean(Files.readString(CONFIG_FILE).trim());
        } catch (IOException ignored) {}
        return true; // 默认开启
    }
}
