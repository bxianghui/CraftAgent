package com.selfagent;

import com.selfagent.cli.CliApp;
import picocli.CommandLine;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        installCrashHandler();
        int exit = new CommandLine(new CliApp()).execute(args);
        System.exit(exit);
    }

    private static void installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            Path logFile = Paths.get(System.getProperty("user.home"), ".self-agent", "crash.log");
            try {
                Files.createDirectories(logFile.getParent());
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println("=== Crash Report ===");
                pw.println("Time:   " + Instant.now());
                pw.println("Thread: " + thread.getName() + " (id=" + thread.getId() + ")");
                pw.println("Error:  " + ex);
                pw.println();
                ex.printStackTrace(pw);
                pw.println();
                String entry = sw.toString();
                // 追加到文件，保留历史崩溃记录
                Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.err.println("[Crash] Log written to: " + logFile);
            } catch (Exception ignored) {}
            // 打印到 stderr 确保用户能看到
            ex.printStackTrace(System.err);
        });
    }
}
