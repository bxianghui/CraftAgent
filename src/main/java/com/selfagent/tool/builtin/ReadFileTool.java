package com.selfagent.tool.builtin;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "Absolute file path to read");
        props.putObject("start_line").put("type", "integer").put("description", "1-based line number to start reading from, inclusive (default: 1)");
        props.putObject("end_line").put("type", "integer").put("description", "1-based line number to stop reading at, inclusive (default: last line). If larger than file length, reads to EOF.");
        schema.putArray("required").add("path");
        return new ToolDefinition("read_file",
            "Read the contents of a file with line numbers. Use start_line/end_line to read specific ranges. " +
            "For files larger than 500 lines, ALWAYS specify start_line and end_line to avoid large output. " +
            "Use this to view source code, config files, or any text file.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String pathStr = (String) params.get("path");
        Path path = Path.of(pathStr);
        try {
            List<String> lines = Files.readAllLines(path);
            int start = params.containsKey("start_line")
                ? ((Number) params.get("start_line")).intValue() - 1 : 0;
            int end = params.containsKey("end_line")
                ? ((Number) params.get("end_line")).intValue() : lines.size();
            start = Math.max(0, start);
            end = Math.min(lines.size(), end);
            // 未指定范围且文件超过 500 行时，只返回前 500 行并提示
            boolean rangeSpecified = params.containsKey("start_line") || params.containsKey("end_line");
            if (!rangeSpecified && lines.size() > 500) {
                end = 500;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(i + 1).append('\t').append(lines.get(i)).append('\n');
            }
            if (!rangeSpecified && lines.size() > 500) {
                sb.append("\n...(file has ").append(lines.size()).append(" lines, showing first 500. Use start_line/end_line to read specific ranges.)");
            }
            return ToolResult.ok(sb.toString());
        } catch (IOException e) {
            return ToolResult.error("Cannot read file: " + e.getMessage());
        }
    }
}
