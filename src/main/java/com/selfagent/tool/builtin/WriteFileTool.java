package com.selfagent.tool.builtin;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class WriteFileTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "Absolute file path to write to (file will be created if it does not exist)");
        props.putObject("content").put("type", "string").put("description", "Complete file content to write. This OVERWRITES the entire file.");
        schema.putArray("required").add("path").add("content");
        return new ToolDefinition("write_file",
            "Write or completely overwrite a file with new content. Use this to create new files or fully replace existing ones. " +
            "For modifying only part of an existing file, use edit_file instead to avoid overwriting other content.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String pathStr = (String) params.get("path");
        String content = (String) params.get("content");
        Path path = Path.of(pathStr);
        if (!ctx.isWriteAllowed(path)) {
            return ToolResult.ok("Write denied: " + pathStr + " is in a protected path. " +
                "[Inform the user that writing to this path is blocked by sandbox policy, and suggest an alternative location under the working directory.]");
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            return ToolResult.ok("Written " + content.length() + " chars to " + pathStr);
        } catch (IOException e) {
            return ToolResult.error("Cannot write file: " + e.getMessage());
        }
    }
}
