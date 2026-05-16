package com.selfagent.tool;

import com.selfagent.tool.builtin.ReadFileTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {
    ReadFileTool tool = new ReadFileTool();

    @Test
    void readsEntireFile(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("hello.txt");
        Files.writeString(f, "line1\nline2\nline3");
        ExecutionContext ctx = new ExecutionContext(tmp, false, System.out);
        ToolResult result = tool.execute(Map.of("path", f.toString()), ctx);
        assertFalse(result.isError);
        assertTrue(result.content.contains("line1"));
        assertTrue(result.content.contains("line3"));
    }

    @Test
    void readsLineRange(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("multi.txt");
        Files.writeString(f, "a\nb\nc\nd\ne");
        ExecutionContext ctx = new ExecutionContext(tmp, false, System.out);
        ToolResult result = tool.execute(Map.of("path", f.toString(), "start_line", 2, "end_line", 4), ctx);
        assertFalse(result.isError);
        assertTrue(result.content.contains("b"));
        assertTrue(result.content.contains("d"));
        assertFalse(result.content.contains("a"));
    }

    @Test
    void returnsErrorForMissingFile(@TempDir Path tmp) {
        ExecutionContext ctx = new ExecutionContext(tmp, false, System.out);
        ToolResult result = tool.execute(Map.of("path", "/nonexistent/file.txt"), ctx);
        assertTrue(result.isError);
    }
}
