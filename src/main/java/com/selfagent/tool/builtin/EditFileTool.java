package com.selfagent.tool.builtin;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class EditFileTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "Absolute file path to edit");
        props.putObject("old_string").put("type", "string").put("description", "The exact string to find and replace (case-sensitive, must match character-for-character including whitespace and indentation). ALL occurrences in the file will be replaced — include enough surrounding context to make it specific.");
        props.putObject("new_string").put("type", "string").put("description", "The replacement string. Can be empty to delete the matched text.");
        schema.putArray("required").add("path").add("old_string").add("new_string");
        return new ToolDefinition("edit_file",
            "Make a targeted edit to a file by replacing an exact string with new content. " +
            "ALL occurrences of old_string will be replaced — make it specific enough to match only the intended location. " +
            "Always read_file first to get the exact text including indentation.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String pathStr = (String) params.get("path");
        String oldStr = (String) params.get("old_string");
        String newStr = (String) params.get("new_string");
        Path path = Path.of(pathStr);
        if (!ctx.isWriteAllowed(path)) {
            return ToolResult.ok("Write denied: " + pathStr + " is in a protected path. " +
                "[Inform the user that editing this path is blocked by sandbox policy.]");
        }
        try {
            String content = Files.readString(path);
            if (!content.contains(oldStr)) {
                return ToolResult.error("old_string not found in file: " + pathStr);
            }
            Files.writeString(path, content.replace(oldStr, newStr));
            return ToolResult.ok("Replaced in " + pathStr);
        } catch (IOException e) {
            return ToolResult.error("Edit failed: " + e.getMessage());
        }
    }
}
