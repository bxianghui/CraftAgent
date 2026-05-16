package com.selfagent.hook;

import java.util.List;
import java.util.Map;

public record HookOutput(
    boolean continueExecution,
    String stopReason,
    String systemMessage,
    String permissionDecision,
    Map<String, Object> updatedInput,
    String updatedToolOutput,
    String additionalContext,
    String initialUserMessage
) {
    public static HookOutput allow() {
        return new HookOutput(true, null, null, "allow", null, null, null, null);
    }

    public static HookOutput block(String reason) {
        return new HookOutput(false, reason, null, "deny", null, null, null, null);
    }

    public static HookOutput merge(List<HookOutput> outputs) {
        if (outputs.isEmpty()) return HookOutput.allow();
        boolean continueExec = outputs.stream().allMatch(HookOutput::continueExecution);
        String stopReason = outputs.stream().filter(o -> o.stopReason() != null)
            .map(HookOutput::stopReason).findFirst().orElse(null);
        String systemMsg = outputs.stream().filter(o -> o.systemMessage() != null)
            .map(HookOutput::systemMessage).findFirst().orElse(null);

        // 权限优先级：deny > ask > allow
        String perm = "allow";
        for (HookOutput o : outputs) {
            if ("deny".equals(o.permissionDecision())) { perm = "deny"; break; }
            if ("ask".equals(o.permissionDecision())) perm = "ask";
        }

        Map<String, Object> updatedInput = outputs.stream()
            .filter(o -> o.updatedInput() != null).map(HookOutput::updatedInput)
            .reduce((a, b) -> b).orElse(null);
        String updatedOutput = outputs.stream()
            .filter(o -> o.updatedToolOutput() != null).map(HookOutput::updatedToolOutput)
            .reduce((a, b) -> b).orElse(null);
        String ctx = outputs.stream().filter(o -> o.additionalContext() != null)
            .map(HookOutput::additionalContext)
            .reduce((a, b) -> a + "\n" + b).orElse(null);
        String initMsg = outputs.stream().filter(o -> o.initialUserMessage() != null)
            .map(HookOutput::initialUserMessage).findFirst().orElse(null);

        return new HookOutput(continueExec, stopReason, systemMsg, perm,
            updatedInput, updatedOutput, ctx, initMsg);
    }
}
