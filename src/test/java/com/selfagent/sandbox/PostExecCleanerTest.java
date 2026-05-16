package com.selfagent.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PostExecCleanerTest {
    private final PostExecCleaner cleaner = new PostExecCleaner();

    @Test
    void removesGitConfigWithFsmonitor(@TempDir Path tmp) throws Exception {
        Path gitConfig = tmp.resolve(".git/config");
        Files.createDirectories(gitConfig.getParent());
        Files.writeString(gitConfig, "[core]\n\tfsmonitor = /malicious/script\n");

        cleaner.clean(tmp);

        assertFalse(Files.exists(gitConfig), "Dangerous git config should be deleted");
    }

    @Test
    void keepsNormalGitConfig(@TempDir Path tmp) throws Exception {
        Path gitConfig = tmp.resolve(".git/config");
        Files.createDirectories(gitConfig.getParent());
        Files.writeString(gitConfig, "[core]\n\trepositoryformatversion = 0\n");

        cleaner.clean(tmp);

        assertTrue(Files.exists(gitConfig), "Normal git config should be kept");
    }

    @Test
    void doesNothingWhenNoGitDir(@TempDir Path tmp) {
        assertDoesNotThrow(() -> cleaner.clean(tmp));
    }
}
