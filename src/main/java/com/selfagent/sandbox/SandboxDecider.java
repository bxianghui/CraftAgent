package com.selfagent.sandbox;

import java.util.List;
import java.util.regex.Pattern;

public class SandboxDecider {
    public enum Decision { ALLOW, REQUIRE_APPROVAL, BLOCK }

    private static final List<Pattern> BLOCK_PATTERNS = List.of(
        // 文件系统破坏
        Pattern.compile("rm\\s+-rf"),
        Pattern.compile("mkfs\\."),
        Pattern.compile("dd\\s+if="),
        Pattern.compile("shred\\s+"),
        Pattern.compile(">\\s*/dev/[sh]d[a-z]"),       // 直接写入磁盘设备

        // 权限破坏
        Pattern.compile("chmod\\s+-R\\s+[0-7]*7[0-7][0-7]\\s+/"), // chmod 777 /...
        Pattern.compile("chown\\s+-R\\s+.*\\s+/(?!tmp|var/tmp)"),  // chown -R ... / 排除 /tmp

        // 进程破坏
        Pattern.compile("kill\\s+-9\\s+1\\b"),          // kill init/launchd
        Pattern.compile("kill\\s+-KILL\\s+1\\b"),
        Pattern.compile("pkill\\s+-9\\s+-[Uu]\\s+root"),// 杀掉所有 root 进程

        // git 危险操作
        Pattern.compile("git\\s+push\\s+--force"),

        // fork bomb
        Pattern.compile(":\\s*\\(\\s*\\)\\s*\\{"),

        // 系统配置破坏
        Pattern.compile("mv\\s+.*/etc/passwd"),
        Pattern.compile("mv\\s+.*/etc/shadow"),
        Pattern.compile("truncate\\s+-s\\s+0\\s+.*/etc/")
    );

    public Decision decide(String cmd, SandboxConfig config) {
        if (cmd == null || cmd.isBlank()) return Decision.ALLOW;
        for (Pattern p : BLOCK_PATTERNS) {
            if (p.matcher(cmd).find()) return Decision.BLOCK;
        }
        for (String glob : config.allowCommands) {
            if (matchesGlob(cmd, glob)) return Decision.ALLOW;
        }
        return Decision.REQUIRE_APPROVAL;
    }

    private boolean matchesGlob(String cmd, String glob) {
        if (glob.endsWith(" *")) {
            String prefix = glob.substring(0, glob.length() - 2);
            return cmd.equals(prefix) || cmd.startsWith(prefix + " ");
        }
        return cmd.equals(glob);
    }
}
