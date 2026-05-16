package com.selfagent.tool.builtin;

import com.selfagent.sandbox.*;
import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BashTool implements ToolPlugin {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 120;

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("cmd").put("type", "string").put("description", "Shell command to execute. Runs in the current working directory.");
        schema.putArray("required").add("cmd");
        return new ToolDefinition("bash",
            "Execute a shell command and return its output. Use for: running tests, compiling code, " +
            "installing dependencies, git operations, checking file existence, or any system operation. " +
            "Blocked commands: rm -rf, git push --force, mkfs., fork bombs. " +
            "Times out after 120s. Large outputs are truncated at 10000 chars.", schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> params, ExecutionContext ctx) {
        String cmd = (String) params.get("cmd");
        if (cmd == null || cmd.isBlank()) return ToolResult.error("cmd parameter is required");

        SandboxConfig sandboxConfig = ctx.sandboxConfig != null ? ctx.sandboxConfig : new SandboxConfig();
        SandboxDecider decider = new SandboxDecider();
        SandboxDecider.Decision decision = decider.decide(cmd, sandboxConfig);

        if (decision == SandboxDecider.Decision.BLOCK) {
            return ToolResult.ok("Command blocked by security policy: " + cmd +
                "\n[Inform the user this command is permanently blocked for safety reasons and suggest a safer alternative.]");
        }

        if (decision == SandboxDecider.Decision.REQUIRE_APPROVAL) {
            ApprovalGate gate = new ApprovalGate(ctx.autoApprove,
                ctx.terminalWriter != null ? ctx.terminalWriter : new java.io.PrintWriter(ctx.out, true),
                ctx.lineReader);
            if (!gate.ask(cmd)) {
                return ToolResult.ok("Command rejected by user: " + cmd +
                    "\n[The user declined to execute this command. Ask if they want a different approach.]");
            }
        }

        // 沙箱包装
        String actualCmd = cmd;
        if (sandboxConfig.enabled && ctx.workingDir != null && ctx.sandboxRuntime != null) {
            actualCmd = ctx.sandboxRuntime.wrap(cmd, ctx.workingDir, sandboxConfig);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", actualCmd);
            pb.directory(ctx.workingDir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return ToolResult.error("Command timed out after " + TIMEOUT_SECONDS + "s");
            }
            int exitCode = proc.exitValue();
            String raw = out.toString();
            String output = raw.isEmpty() ? "(no output)" : raw;
            if (output.length() > 10000) {
                output = output.substring(0, 10000) + "\n...(output truncated at 10000 chars)";
            }
            // 执行后清理 bare repo 逃逸风险
            if (ctx.workingDir != null) {
                new PostExecCleaner().clean(ctx.workingDir);
            }
            if (exitCode != 0) return ToolResult.ok(
                "Exit " + exitCode + ": " + output +
                "\n[Command failed. Analyze the error above and provide the user with a clear explanation and actionable solution.]");
            return ToolResult.ok(output);
        } catch (IOException | InterruptedException e) {
            return ToolResult.error("Execution error: " + e.getMessage());
        }
    }
}
