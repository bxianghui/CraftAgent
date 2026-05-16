package com.selfagent.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PostExecCleaner {
    private static final List<String> DANGEROUS_KEYS = List.of(
        "fsmonitor", "hookspath", "core.hookspath", "core.fsmonitor"
    );

    public void clean(Path workingDir) {
        Path gitConfig = workingDir.resolve(".git/config");
        if (!Files.exists(gitConfig)) return;
        try {
            String content = Files.readString(gitConfig);
            boolean dangerous = DANGEROUS_KEYS.stream()
                .anyMatch(key -> content.toLowerCase().contains(key.toLowerCase()));
            if (dangerous) {
                Files.delete(gitConfig);
                System.err.println("[Sandbox] ⚠️  Removed dangerous .git/config containing hook injection: " + gitConfig);
            }
        } catch (IOException e) {
            System.err.println("[Sandbox] Failed to check .git/config: " + e.getMessage());
        }
    }
}
