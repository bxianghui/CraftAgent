package com.selfagent.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HookManagerTest {
    @Test
    void noHooksFileReturnsAllow() {
        HookManager mgr = HookManager.loadFrom(Path.of("/nonexistent/config.hooks.json"), null);
        HookInput input = new HookInput("PreToolUse", "s1", "bash",
            null, null, false, null, null, "/tmp");
        HookOutput out = mgr.fire("PreToolUse", input);
        assertTrue(out.continueExecution());
    }

    @Test
    void matcherStarMatchesAllTools(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("config.hooks.json");
        Files.writeString(cfg, "{\"hooks\":{\"PreToolUse\":[{\"matcher\":\"*\",\"hooks\":[" +
            "{\"type\":\"command\",\"command\":\"exit 0\",\"timeout\":5}" +
            "]}]}}");
        HookManager mgr = HookManager.loadFrom(cfg, null);
        HookInput input = new HookInput("PreToolUse", "s1", "bash",
            null, null, false, null, null, "/tmp");
        assertTrue(mgr.fire("PreToolUse", input).continueExecution());
    }

    @Test
    void matcherPipeFiltersByToolName(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("config.hooks.json");
        Files.writeString(cfg, "{\"hooks\":{\"PreToolUse\":[{\"matcher\":\"write_file|edit_file\",\"hooks\":[" +
            "{\"type\":\"command\",\"command\":\"exit 2\",\"timeout\":5}" +
            "]}]}}");
        HookManager mgr = HookManager.loadFrom(cfg, null);
        // bash 不匹配 → 放行
        HookInput bashInput = new HookInput("PreToolUse", "s1", "bash",
            null, null, false, null, null, "/tmp");
        assertTrue(mgr.fire("PreToolUse", bashInput).continueExecution());
        // write_file 匹配 → 阻断
        HookInput writeInput = new HookInput("PreToolUse", "s1", "write_file",
            null, null, false, null, null, "/tmp");
        assertFalse(mgr.fire("PreToolUse", writeInput).continueExecution());
    }

    @Test
    void noHooksForEventReturnsAllow(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("config.hooks.json");
        Files.writeString(cfg, "{\"hooks\":{\"SessionStart\":[]}}");
        HookManager mgr = HookManager.loadFrom(cfg, null);
        HookInput input = new HookInput("PreToolUse", "s1", "bash",
            null, null, false, null, null, "/tmp");
        assertTrue(mgr.fire("PreToolUse", input).continueExecution());
    }
}
