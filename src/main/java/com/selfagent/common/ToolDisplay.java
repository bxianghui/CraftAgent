package com.selfagent.common;

import java.util.Map;

/**
 * 工具调用终端显示：⚙ 执行中 → ✓ 完成 / ✗ 失败，\r 覆盖同一行。
 */
public class ToolDisplay {
    private static final String YELLOW = "\033[33m";
    private static final String GREEN  = "\033[32m";
    private static final String RED    = "\033[31m";
    private static final String GRAY   = "\033[90m";
    private static final String RESET  = "\033[0m";
    private static final Object LOCK   = new Object();

    public static void start(String toolName, Map<String, Object> arguments) {
        String summary = buildSummary(toolName, arguments);
        synchronized (LOCK) {
            System.out.println(YELLOW + "⚙ " + summary + RESET);
        }
    }

    public static void success(String toolName, Map<String, Object> arguments, long durationMs) {
        String summary = buildSummary(toolName, arguments);
        synchronized (LOCK) {
            System.out.println(GREEN + "✓ " + summary + "  (" + durationMs + "ms)" + RESET);
        }
    }

    public static void failure(String toolName, Map<String, Object> arguments, String reason) {
        String summary = buildSummary(toolName, arguments);
        synchronized (LOCK) {
            System.out.println(RED + "✗ " + summary + "  " + reason + RESET);
        }
    }

    public static void skillStart(String skillName) {
        synchronized (LOCK) {
            System.out.println(YELLOW + "⚙ Skill(" + skillName + ")" + RESET);
        }
    }

    public static void skillSuccess(String skillName, long durationMs) {
        synchronized (LOCK) {
            System.out.println(GREEN + "✓ Skill(" + skillName + ")  (" + durationMs + "ms)" + RESET);
        }
    }

    public static void thinkingPrefix() {
        synchronized (LOCK) {
            System.out.print(GRAY + "[thinking] " + RESET);
            System.out.flush();
        }
    }

    public static void agentStart(String agentType, String description) {
        synchronized (LOCK) {
            System.out.println(YELLOW + "⚙ Agent(" + agentType + ")  " + truncate(description, 50) + RESET);
        }
    }

    public static void agentSuccess(String agentType, String description, long durationMs) {
        synchronized (LOCK) {
            System.out.println(GREEN + "✓ Agent(" + agentType + ")  "
                + truncate(description, 50) + "  (" + durationMs + "ms)" + RESET);
        }
    }

    public static void agentFailure(String agentType, String description, String reason) {
        synchronized (LOCK) {
            System.out.println(RED + "✗ Agent(" + agentType + ")  "
                + truncate(description, 50) + "  " + truncate(reason, 40) + RESET);
        }
    }

    public static void bgAgentStart(String agentType, String description) {
        synchronized (LOCK) {
            System.out.println(GRAY + "⚙ [bg] Agent(" + agentType + ")  "
                + truncate(description, 50) + RESET);
        }
    }

    public static void bgAgentSuccess(String agentType, String description, long durationMs) {
        synchronized (LOCK) {
            System.out.println(GREEN + "✓ [bg] Agent(" + agentType + ")  "
                + truncate(description, 50) + "  (" + durationMs + "ms)" + RESET);
        }
    }

    public static void bgAgentFailure(String agentType, String description, String reason) {
        synchronized (LOCK) {
            System.out.println(RED + "✗ [bg] Agent(" + agentType + ")  "
                + truncate(description, 50) + "  " + truncate(reason, 40) + RESET);
        }
    }

    private static String buildSummary(String toolName, Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return toolName;
        String arg = extractMainArg(toolName, arguments);
        return arg.isEmpty() ? toolName : toolName + "  " + truncate(arg, 60);
    }

    private static String extractMainArg(String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "bash"        -> str(arguments.get("cmd"));
            case "read_file"   -> str(arguments.get("path"));
            case "write_file"  -> str(arguments.get("path"));
            case "edit_file"   -> str(arguments.get("path"));
            case "list_files"  -> str(arguments.get("path"));
            case "search_code" -> str(arguments.get("pattern"));
            case "web_fetch"   -> str(arguments.get("url"));
            case "search_docs" -> str(arguments.get("query"));
            default -> arguments.values().stream()
                .filter(v -> v instanceof String)
                .map(Object::toString)
                .findFirst().orElse("");
        };
    }

    private static String str(Object v) { return v instanceof String s ? s : ""; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
