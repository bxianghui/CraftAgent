package com.selfagent.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session 内的临时记忆容器，内容在 session 结束后丢弃。
 * /memory 命令可查看当前内容，/remember 命令可手动写入。
 */
public class SessionMemory {
    private final Map<String, String> entries = new LinkedHashMap<>();

    public void put(String key, String value) {
        entries.put(key, value);
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * 格式化为 Markdown 块，追加到 system prompt 末尾。
     * 空时返回空字符串，不注入任何内容。
     */
    public String toSystemPromptBlock() {
        if (entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n## Session Memory\n");
        entries.forEach((k, v) -> sb.append("- **").append(k).append("**: ").append(v).append("\n"));
        return sb.toString();
    }
}
