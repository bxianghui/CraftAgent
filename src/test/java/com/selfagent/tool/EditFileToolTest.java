package com.selfagent.tool;

import com.selfagent.tool.builtin.EditFileTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EditFileToolTest {
    EditFileTool tool = new EditFileTool();

    @Test
    void replacesExactString(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("code.java");
        Files.writeString(f, "public class Foo {\n    int x = 1;\n}\n");
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        ToolResult result = tool.execute(Map.of(
            "path", f.toString(),
            "old_string", "int x = 1;",
            "new_string", "int x = 42;"
        ), ctx);
        assertFalse(result.isError);
        assertTrue(Files.readString(f).contains("int x = 42;"));
        assertFalse(Files.readString(f).contains("int x = 1;"));
    }

    @Test
    void returnsErrorWhenOldStringNotFound(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("code.java");
        Files.writeString(f, "public class Foo {}");
        ExecutionContext ctx = new ExecutionContext(tmp, true, System.out);
        ToolResult result = tool.execute(Map.of(
            "path", f.toString(),
            "old_string", "nonexistent string",
            "new_string", "replacement"
        ), ctx);
        assertTrue(result.isError);
    }
}
