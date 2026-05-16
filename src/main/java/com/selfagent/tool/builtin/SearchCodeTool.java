package com.selfagent.tool.builtin;

import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.regex.*;

public class SearchCodeTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final java.util.Set<String> PRIVACY_DIRS = java.util.Set.of(
        "Library/Application Support/CallHistoryTransactions",
        "Library/Application Support/AddressBook",
        "Library/Application Support/MobileSync",
        "Library/Application Support/com.apple.TCC",
        "Library/Calendars", "Library/Messages", "Library/Mail",
        "Library/Safari", "Library/Health", "Library/HomeKit", "Library/Cookies"
    );

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("pattern").put("type", "string").put("description", "Regex pattern to search for in file contents (case-sensitive). Use literal strings or regex, e.g. 'myMethod', 'class\\s+Foo'");
        props.putObject("path").put("type", "string").put("description", "Absolute directory path to search recursively. ALWAYS provide this — omitting it searches the entire working directory which can be very slow.");
        props.putObject("glob").put("type", "string").put("description", "Glob pattern to filter files, e.g. '*.java', '**/*.py', 'src/**/*.ts' (default: '**' matches all files). Use this to narrow results.");
        schema.putArray("required").add("pattern");
        return new ToolDefinition("search_code",
            "Search for a regex pattern across file contents and return matching lines with file paths and line numbers. " +
            "Use this to find where a function is defined or used, locate a class, find TODO comments, " +
            "or discover all usages of a variable. Results are limited to 500 matches to avoid large output.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String pattern = (String) params.get("pattern");
        String pathStr = params.containsKey("path") ? (String) params.get("path") : ctx.workingDir.toString();
        String glob = (String) params.getOrDefault("glob", "**");
        Path dir = Path.of(pathStr);
        try {
            Pattern regex = Pattern.compile(pattern);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            StringBuilder sb = new StringBuilder();
            int[] count = {0};
            final int MAX_RESULTS = 500;
            Files.walkFileTree(dir, java.util.EnumSet.noneOf(FileVisitOption.class), 10,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                        String rel = dir.relativize(d).toString();
                        for (String blocked : PRIVACY_DIRS) {
                            if (rel.startsWith(blocked)) return FileVisitResult.SKIP_SUBTREE;
                        }
                        if (!d.equals(dir) && !d.toFile().canRead()) return FileVisitResult.SKIP_SUBTREE;
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (count[0] >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                        Path rel = dir.relativize(file);
                        if (matcher.matches(rel)) {
                            try {
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size() && count[0] < MAX_RESULTS; i++) {
                                    if (regex.matcher(lines.get(i)).find()) {
                                        sb.append(rel).append(':').append(i + 1)
                                          .append(": ").append(lines.get(i)).append('\n');
                                        count[0]++;
                                    }
                                }
                            } catch (IOException ignored) {}
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            if (sb.isEmpty()) return ToolResult.ok("(no matches)");
            if (count[0] >= MAX_RESULTS) sb.append("\n...(truncated at ").append(MAX_RESULTS).append(" matches. Use a more specific pattern or glob to narrow results.)");
            return ToolResult.ok(sb.toString());
        } catch (PatternSyntaxException e) {
            return ToolResult.error("Invalid regex: " + e.getMessage());
        } catch (IOException e) {
            return ToolResult.error("Search failed: " + e.getMessage());
        }
    }
}
