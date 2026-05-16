package com.selfagent.hook.executor;

import com.selfagent.hook.HookConfig;
import com.selfagent.hook.HookInput;
import com.selfagent.hook.HookOutput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CommandHookExecutor {
    private static final ObjectMapper mapper = new ObjectMapper();

    public HookOutput execute(HookConfig.HookEntry entry, HookInput input) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", entry.command);
            pb.environment().put("HOOK_INPUT_JSON", input.toJson());
            pb.environment().put("TOOL_NAME", input.toolName() != null ? input.toolName() : "");
            pb.environment().put("TOOL_RESULT", input.toolResult() != null ? input.toolResult() : "");
            pb.environment().put("USER_PROMPT", input.userPrompt() != null ? input.userPrompt() : "");
            pb.environment().put("SESSION_ID", input.sessionId() != null ? input.sessionId() : "");
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                 BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                out.lines().forEach(l -> stdout.append(l).append("\n"));
                err.lines().forEach(l -> stderr.append(l).append("\n"));
            }

            boolean finished = proc.waitFor(entry.timeout, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                System.err.println("[Hook] Command timed out: " + entry.command);
                return HookOutput.allow();
            }

            int exitCode = proc.exitValue();
            if (exitCode == 2) {
                String reason = stderr.toString().trim();
                if (reason.isEmpty()) reason = "Blocked by hook";
                System.err.println("[Hook] Blocked: " + reason);
                return HookOutput.block(reason);
            }
            if (exitCode != 0) {
                System.err.println("[Hook] Command failed (exit " + exitCode + "): " + stderr.toString().trim());
                return HookOutput.allow();
            }

            String out = stdout.toString().trim();
            if (out.isEmpty()) return HookOutput.allow();
            return parseOutput(out);
        } catch (Exception e) {
            System.err.println("[Hook] Command error: " + e.getMessage());
            return HookOutput.allow();
        }
    }

    public static HookOutput parseOutput(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            boolean cont = root.path("continue").asBoolean(true);
            String stopReason = nullIfBlank(root.path("stopReason").asText(null));
            String systemMsg = nullIfBlank(root.path("systemMessage").asText(null));
            JsonNode specific = root.path("hookSpecificOutput");
            String perm = specific.path("permissionDecision").asText("allow");
            Map<String, Object> updatedInput = specific.has("updatedInput")
                ? mapper.convertValue(specific.get("updatedInput"), Map.class) : null;
            String updatedOutput = nullIfBlank(specific.path("updatedToolOutput").asText(null));
            String addCtx = nullIfBlank(specific.path("additionalContext").asText(null));
            String initMsg = nullIfBlank(specific.path("initialUserMessage").asText(null));
            return new HookOutput(cont, stopReason, systemMsg, perm,
                updatedInput, updatedOutput, addCtx, initMsg);
        } catch (Exception e) {
            System.err.println("[Hook] Failed to parse hook output: " + e.getMessage());
            return HookOutput.allow();
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
