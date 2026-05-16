package com.selfagent.tool;

import com.selfagent.tool.builtin.WriteFileTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WriteFileToolTest {
    WriteFileTool tool = new WriteFileTool();

    @Test
    void writesNewFile(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("out.txt");
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        ToolResult result = tool.execute(Map.of("path", f.toString(), "content", "hello"), ctx);
        assertFalse(result.isError);
        assertEquals("hello", Files.readString(f));
    }

    @Test
    void overwritesExistingFile(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("existing.txt");
        Files.writeString(f, "old content");
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        tool.execute(Map.of("path", f.toString(), "content", "new content"), ctx);
        assertEquals("new content", Files.readString(f));
    }
}
