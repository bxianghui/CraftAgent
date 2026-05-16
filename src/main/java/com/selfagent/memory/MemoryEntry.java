package com.selfagent.memory;

/**
 * 长期记忆条目，对应 ~/.self-agent/memory/ 下的一个 Markdown 文件。
 *
 * type 枚举：
 *   user      — 用户背景信息、技术栈、偏好
 *   feedback  — 用户对 AI 行为的反馈和偏好
 *   project   — 项目状态、架构决策、重要上下文
 *   reference — 外部资源、文档地址
 *   task      — 进行中的任务状态
 */
public class MemoryEntry {
    public String name;
    public String description;  // 一行摘要，写入 MEMORY.md 索引，用于关键词检索
    public String type;
    public String content;      // 完整正文，仅在 loadEntry() 时从文件加载
    public String filename;     // 对应的 .md 文件名，由 toFilename(name) 生成
    public String updatedAt;

    public MemoryEntry() {}

    public MemoryEntry(String name, String description, String type, String content) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.content = content;
        this.updatedAt = java.time.Instant.now().toString();
    }
}
