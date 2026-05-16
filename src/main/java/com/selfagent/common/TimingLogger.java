package com.selfagent.common;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 计时日志工具。配合 @Timed 注解使用，支持运行时开关和持久化配置。
 * 持久化路径：~/.self-agent/timing.enabled（内容为 true/false）
 */
public class TimingLogger {
    private static final Path CONFIG_FILE =
        Paths.get(System.getProperty("user.home"), ".self-agent", "timing.enabled");
    private static final AtomicBoolean enabled = new AtomicBoolean(loadFromDisk());

    public static void log(String label, long startMs) {
        if (!enabled.get()) return;
        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("[Timing] %-30s %dms%n", label, elapsed);
    }

    public static boolean isEnabled() {
        return enabled.get();
    }

    public static void setEnabled(boolean on) {
        enabled.set(on);
        saveToDisk(on);
    }

    private static boolean loadFromDisk() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                return Boolean.parseBoolean(Files.readString(CONFIG_FILE).trim());
            }
        } catch (IOException ignored) {}
        return false; // 默认关闭
    }

    private static void saveToDisk(boolean on) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, String.valueOf(on));
        } catch (IOException e) {
            System.err.println("[TimingLogger] Failed to persist: " + e.getMessage());
        }
    }
}
