package com.selfagent.hook;

import com.selfagent.hook.executor.*;
import com.selfagent.model.LLMProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class HookManager {
    private static final Path DEFAULT_CONFIG =
        Paths.get(System.getProperty("user.home"), ".self-agent", "config.hooks.json");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HookConfig config;
    private final LLMProvider provider;
    private final ExecutorService asyncPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "hook-async");
        t.setDaemon(true);
        return t;
    });

    private HookManager(HookConfig config, LLMProvider provider) {
        this.config = config;
        this.provider = provider;
    }

    public static HookManager load(LLMProvider provider) {
        return loadFrom(DEFAULT_CONFIG, provider);
    }

    public static HookManager loadFrom(Path configPath, LLMProvider provider) {
        if (!Files.exists(configPath)) {
            return new HookManager(new HookConfig(), provider);
        }
        try {
            HookConfig cfg = mapper.readValue(configPath.toFile(), HookConfig.class);
            return new HookManager(cfg, provider);
        } catch (Exception e) {
            System.err.println("[Hook] Failed to load config: " + e.getMessage());
            return new HookManager(new HookConfig(), provider);
        }
    }

    public boolean hasHooks(String event) {
        List<HookConfig.HookGroup> groups = config.hooks.get(event);
        return groups != null && !groups.isEmpty();
    }

    public HookOutput fire(String event, HookInput input) {
        List<HookConfig.HookGroup> groups = config.hooks.get(event);
        if (groups == null || groups.isEmpty()) return HookOutput.allow();

        List<HookOutput> results = new ArrayList<>();
        for (HookConfig.HookGroup group : groups) {
            if (!matchesTool(group.matcher, input.toolName(), input.userPrompt())) continue;
            for (HookConfig.HookEntry entry : group.hooks) {
                if (entry.async) {
                    asyncPool.submit(() -> executeOne(entry, input));
                    continue;
                }
                results.add(executeOne(entry, input));
            }
        }
        return HookOutput.merge(results);
    }

    public void fireAsync(String event, HookInput input) {
        asyncPool.submit(() -> fire(event, input));
    }

    private HookOutput executeOne(HookConfig.HookEntry entry, HookInput input) {
        return switch (entry.type) {
            case "command" -> new CommandHookExecutor().execute(entry, input);
            case "http"    -> new HttpHookExecutor().execute(entry, input);
            case "prompt"  -> provider != null
                ? new PromptHookExecutor(provider).execute(entry, input)
                : HookOutput.allow();
            default -> {
                System.err.println("[Hook] Unknown type: " + entry.type);
                yield HookOutput.allow();
            }
        };
    }

    private boolean matchesTool(String matcher, String toolName, String userPrompt) {
        if (matcher == null || matcher.equals("*")) return true;
        String target = toolName != null ? toolName : (userPrompt != null ? "prompt" : "");
        for (String m : matcher.split("\\|")) {
            if (m.trim().equalsIgnoreCase(target)) return true;
        }
        return false;
    }
}
