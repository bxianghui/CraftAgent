package com.selfagent.sandbox;

import java.util.List;

public class SandboxConfig {
    public boolean enabled = false;
    public List<String> allowCommands = List.of(
        // 构建与包管理
        "git *", "mvn *", "gradle *", "npm *", "node *", "python *", "pip *",
        // 文件查看（只读，agent output 监控需要）
        "tail *", "cat *", "head *", "less *", "grep *", "ls *", "find *",
        // 环境检测（只读）
        "which *", "command *", "echo *", "pwd", "whoami", "date", "uname *",
        // 内部 CLI（citadel skill 等）
        "oa-skills *"
    );
    public List<String> denyWritePaths = List.of(
        "~/.ssh", "~/.aws", "~/.gnupg", "~/.config/gh"
    );
    public boolean allowNetwork = true;
}
