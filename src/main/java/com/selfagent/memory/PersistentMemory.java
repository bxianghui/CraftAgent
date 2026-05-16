package com.selfagent.memory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆 — 跨 session 持久化到 ~/.self-agent/memory/。
 * 每条记忆存为独立的 Markdown 文件（YAML frontmatter + 正文），
 * MEMORY.md 作为索引文件，记录 name/filename/description 用于快速检索。
 *
 * 写入时机：
 *   - session 结束时 MemoryExtractor.promoteToLongTerm() 自动提升短时记忆
 *   - /remember 命令手动写入
 *
 * 注入时机：
 *   - 每轮对话前 MemoryInjector 按当前用户输入关键词检索相关条目，注入 system prompt
 */
public class PersistentMemory {
    private final Path memoryDir;
    private static final String INDEX_FILE = "MEMORY.md";

    public PersistentMemory(Path memoryDir) throws IOException {
        this.memoryDir = memoryDir;
        Files.createDirectories(memoryDir);
    }

    public static PersistentMemory defaultMemory() throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), ".self-agent", "memory");
        return new PersistentMemory(dir);
    }

    /** 写入记忆文件并更新 MEMORY.md 索引。同名条目会覆盖。 */
    public void save(MemoryEntry entry) throws IOException {
        String filename = toFilename(entry.name);
        entry.filename = filename;
        entry.updatedAt = Instant.now().toString();

        String content = "---\n" +
            "name: " + entry.name + "\n" +
            "description: " + entry.description + "\n" +
            "type: " + entry.type + "\n" +
            "updatedAt: " + entry.updatedAt + "\n" +
            "---\n\n" +
            entry.content;
        Files.writeString(memoryDir.resolve(filename), content);
        updateIndex(entry, filename);
    }

    /** 读取 MEMORY.md 索引，返回所有条目的摘要（不含正文）。 */
    public List<MemoryEntry> loadIndex() throws IOException {
        Path indexPath = memoryDir.resolve(INDEX_FILE);
        if (!Files.exists(indexPath)) return List.of();
        List<MemoryEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(indexPath)) {
            if (!line.startsWith("- [")) continue;
            // 格式: - [name](filename.md) — description
            int nameStart = line.indexOf('[') + 1;
            int nameEnd = line.indexOf(']');
            int fileStart = line.indexOf('(') + 1;
            int fileEnd = line.indexOf(')');
            int descStart = line.indexOf("— ") + 2;
            if (nameEnd < 0 || fileEnd < 0) continue;
            MemoryEntry e = new MemoryEntry();
            e.name = line.substring(nameStart, nameEnd);
            e.filename = line.substring(fileStart, fileEnd);
            e.description = descStart > 1 ? line.substring(descStart) : "";
            // 跳过索引存在但文件已删除的孤立条目
            if (Files.exists(memoryDir.resolve(e.filename))) {
                entries.add(e);
            }
        }
        return entries;
    }

    /**
     * 基于 TF-IDF 检索相关记忆条目（按相关度降序）。
     * 当条目数 > 20 且 TF-IDF 无匹配时，降级为 LLM 语义检索（需传入 provider，可为 null 跳过降级）。
     * 只返回索引摘要，需要完整正文时调用 loadEntry()。
     */
    public List<MemoryEntry> search(String query, com.selfagent.model.LLMProvider provider) throws IOException {
        List<MemoryEntry> index = loadIndex();
        if (index.isEmpty()) return List.of();

        List<MemoryEntry> results = TfIdfSearcher.search(query, index);

        // TF-IDF 无结果且条目较多时，降级 LLM 语义检索, 金尽可能的不要修改prompt，会打破缓存
        if (results.isEmpty() && index.size() > 40 && provider != null) {
            results = llmSearch(query, index, provider);
        }
        return results;
    }

    /** 保留无 provider 的简化重载，TF-IDF only，无降级 */
    public List<MemoryEntry> search(String query) throws IOException {
        return search(query, null);
    }

    @SuppressWarnings("unchecked")
    private List<MemoryEntry> llmSearch(String query, List<MemoryEntry> index,
                                         com.selfagent.model.LLMProvider provider) {
        try {
            StringBuilder sb = new StringBuilder("以下是记忆条目列表（name — description）：\n");
            for (int i = 0; i < index.size(); i++) {
                sb.append(i).append(". ").append(index.get(i).name)
                  .append(" — ").append(index.get(i).description).append("\n");
            }
            sb.append("\n用户输入：").append(query);
            sb.append("\n\n请返回与用户输入语义相关的条目序号，用逗号分隔，没有则返回空。只输出序号，不要解释。");

            com.selfagent.model.ChatRequest req = new com.selfagent.model.ChatRequest(
                java.util.List.of(java.util.Map.of("role", "user", "content", sb.toString())),
                java.util.List.of(), null, false, null);
            com.selfagent.model.ChatResponse resp = provider.chat(req);
            if (resp.content == null || resp.content.isBlank()) return List.of();

            List<MemoryEntry> results = new ArrayList<>();
            for (String part : resp.content.split("[,，\\s]+")) {
                try {
                    int idx = Integer.parseInt(part.trim());
                    if (idx >= 0 && idx < index.size()) results.add(index.get(idx));
                } catch (NumberFormatException ignored) {}
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 删除 name 完全匹配或 description 包含关键词的所有条目（文件 + 索引）。 */
    public void delete(String nameOrKeyword) throws IOException {
        List<MemoryEntry> index = new ArrayList<>(loadIndex());
        List<MemoryEntry> toDelete = index.stream()
            .filter(e -> e.name.equalsIgnoreCase(nameOrKeyword)
                || e.description.toLowerCase().contains(nameOrKeyword.toLowerCase()))
            .collect(Collectors.toList());
        for (MemoryEntry e : toDelete) {
            Files.deleteIfExists(memoryDir.resolve(e.filename));
            index.remove(e);
        }
        writeIndex(index);
    }

    /** 读取单条记忆的完整内容（解析 frontmatter + 正文）。 */
    public MemoryEntry loadEntry(String filename) throws IOException {
        Path file = memoryDir.resolve(filename);
        if (!Files.exists(file)) return null;
        String raw = Files.readString(file);
        MemoryEntry entry = new MemoryEntry();
        entry.filename = filename;
        if (raw.startsWith("---")) {
            int end = raw.indexOf("---", 3);
            if (end > 0) {
                String fm = raw.substring(3, end).trim();
                for (String line : fm.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    String key = line.substring(0, colon).trim();
                    String val = line.substring(colon + 1).trim();
                    switch (key) {
                        case "name" -> entry.name = val;
                        case "description" -> entry.description = val;
                        case "type" -> entry.type = val;
                        case "updatedAt" -> entry.updatedAt = val;
                    }
                }
                entry.content = raw.substring(end + 3).trim();
            }
        } else {
            entry.content = raw;
        }
        return entry;
    }

    private void updateIndex(MemoryEntry entry, String filename) throws IOException {
        List<MemoryEntry> index = new ArrayList<>(loadIndex());
        index.removeIf(e -> e.name.equals(entry.name));
        index.add(entry);
        writeIndex(index);
    }

    private void writeIndex(List<MemoryEntry> index) throws IOException {
        StringBuilder sb = new StringBuilder("# Memory Index\n\n");
        index.forEach(e -> sb.append("- [").append(e.name).append("](").append(e.filename)
            .append(") — ").append(e.description).append("\n"));
        Files.writeString(memoryDir.resolve(INDEX_FILE), sb.toString());
    }

    private String toFilename(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_") + ".md";
    }
}
