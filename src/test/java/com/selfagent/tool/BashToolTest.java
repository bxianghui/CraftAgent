package com.selfagent.tool;

import com.selfagent.tool.builtin.BashTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BashToolTest {
    BashTool tool = new BashTool();

    @Test
    void executesSimpleCommand(@TempDir Path tmp) {
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        ToolResult result = tool.execute(Map.of("cmd", "echo hello"), ctx);
        assertFalse(result.isError);
        assertTrue(result.content.contains("hello"));
    }

    @Test
    void capturesStderr(@TempDir Path tmp) {
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        ToolResult result = tool.execute(Map.of("cmd", "ls /nonexistent_path_xyz"), ctx);
        assertTrue(result.isError || result.content.length() > 0);
    }

    @Test
    void respectsWorkingDir(@TempDir Path tmp) throws IOException {
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        ToolResult result = tool.execute(Map.of("cmd", "pwd"), ctx);
        assertFalse(result.isError);
        assertTrue(result.content.trim().contains(tmp.toFile().getCanonicalPath()));
    }
}
