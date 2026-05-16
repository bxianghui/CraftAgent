package com.selfagent.tool.builtin;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.stream.Collectors;

public class ListFilesTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final java.util.Set<String> PRIVACY_DIRS = java.util.Set.of(
        "Library/Application Support/CallHistoryTransactions",
        "Library/Application Support/AddressBook",
        "Library/Application Support/MobileSync",
        "Library/Application Support/com.apple.TCC",
        "Library/Calendars",
        "Library/Messages",
        "Library/Mail",
        "Library/Safari",
        "Library/Health",
        "Library/HomeKit",
        "Library/Cookies"
    );

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "Absolute directory path to list. ALWAYS provide this — omitting it scans the entire working directory which can be very slow and produce too many results.");
        props.putObject("glob").put("type", "string").put("description", "Glob pattern to filter results, e.g. '**/*.java', '*.xml' (optional, default: all files)");
        return new ToolDefinition("list_files",
            "List files and directories. Use this to explore project structure, find files by extension, " +
            "or verify a file exists before reading it. Use glob patterns to narrow results for large directories.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String pathStr = params.containsKey("path") ? (String) params.get("path") : ctx.workingDir.toString();
        Object globParam = params.get("glob");
        String glob = (globParam instanceof String s && !s.isBlank()) ? s : "**";
        Path dir = Path.of(pathStr);
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            java.util.List<String> results = new java.util.ArrayList<>();
            // 使用 walkFileTree 代替 Files.walk，遇到无权限目录时跳过而不是抛异常
            Files.walkFileTree(dir, java.util.EnumSet.noneOf(FileVisitOption.class), 5,
                new java.nio.file.SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                        try {
                            Path rel = dir.relativize(file);
                            if (matcher.matches(rel)) results.add(rel.toString());
                        } catch (Exception ignored) {}
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult preVisitDirectory(Path d, java.nio.file.attribute.BasicFileAttributes attrs) {
                        try {
                            Path rel = dir.relativize(d);
                            String relStr = rel.toString();
                            // 屏蔽 macOS 隐私保护目录
                            for (String blocked : PRIVACY_DIRS) {
                                if (relStr.startsWith(blocked) || relStr.contains("/" + blocked.replace("/", File.separator))) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }
                            if (!d.equals(dir) && !d.toFile().canRead()) return FileVisitResult.SKIP_SUBTREE;
                            if (!relStr.isEmpty() && matcher.matches(rel)) results.add(relStr);
                        } catch (Exception ignored) {}
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            results.sort(String::compareTo);
            if (results.isEmpty()) return ToolResult.ok("(empty)");
            String result = String.join("\n", results);
            if (result.length() > 8000) {
                int lines = results.size();
                result = String.join("\n", results.subList(0, Math.min(200, lines)));
                result += "\n...(truncated, showing 200/" + lines + " entries. Use glob pattern to narrow results, e.g. '**/*.java')";
            }
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.ok("List failed: " + e.getMessage() +
                "\n[Analyze the error and provide the user with a clear explanation and actionable solution. Do not stop the conversation.]");
        }
    }
}
