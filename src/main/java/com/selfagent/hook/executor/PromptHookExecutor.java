package com.selfagent.hook.executor;

import com.selfagent.hook.HookConfig;
import com.selfagent.hook.HookInput;
import com.selfagent.hook.HookOutput;
import com.selfagent.model.ChatRequest;
import com.selfagent.model.ChatResponse;
import com.selfagent.model.LLMProvider;
import java.util.List;
import java.util.Map;

public class PromptHookExecutor {
    private final LLMProvider provider;

    public PromptHookExecutor(LLMProvider provider) {
        this.provider = provider;
    }

    public HookOutput execute(HookConfig.HookEntry entry, HookInput input) {
        try {
            String userMsg = entry.prompt
                .replace("$HOOK_INPUT_JSON", input.toJson())
                .replace("$TOOL_NAME", input.toolName() != null ? input.toolName() : "")
                .replace("$USER_PROMPT", input.userPrompt() != null ? input.userPrompt() : "");
            ChatRequest req = new ChatRequest(
                List.of(Map.of("role", "user", "content", userMsg)),
                List.of(), null, false,
                "You are a hook executor. Respond ONLY with valid JSON matching the HookOutput schema. No explanation.");
            ChatResponse resp = provider.chat(req);
            if (resp.content == null || resp.content.isBlank()) return HookOutput.allow();
            String json = resp.content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end < 0) return HookOutput.allow();
            return CommandHookExecutor.parseOutput(json.substring(start, end + 1));
        } catch (Exception e) {
            System.err.println("[Hook] Prompt error: " + e.getMessage());
            return HookOutput.allow();
        }
    }
}
