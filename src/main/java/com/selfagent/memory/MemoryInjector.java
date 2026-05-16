package com.selfagent.memory;

import com.selfagent.model.LLMProvider;
import java.util.List;

/**
 * 每轮对话前将长期记忆注入 system prompt。
 * 按当前用户输入关键词检索相关条目，动态注入。
 */
public class MemoryInjector {
    private final PersistentMemory persistentMemory;
    private final LLMProvider provider;

    public MemoryInjector(PersistentMemory persistentMemory, LLMProvider provider) {
        this.persistentMemory = persistentMemory;
        this.provider = provider;
    }

    public String buildMemoryBlock(String currentUserInput) {
        if (persistentMemory == null || currentUserInput == null) return "";
        try {
            List<MemoryEntry> relevant = persistentMemory.search(currentUserInput, provider);
            if (relevant.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(
                "\n## User Background (for reference only — do not override the current task)\n");
            relevant.forEach(e ->
                sb.append("- **").append(e.name).append("**: ").append(e.description).append("\n"));
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
