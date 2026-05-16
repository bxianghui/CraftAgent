package com.selfagent.sandbox;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MacOSSandboxRuntime implements SandboxRuntime {

    @Override
    public boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) return false;
        try {
            Process p = new ProcessBuilder("which", "sandbox-exec").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String wrap(String cmd, Path workingDir, SandboxConfig config) {
        String profile = buildProfile(workingDir, config);
        String escapedProfile = profile.replace("'", "'\\''");
        String escapedCmd = cmd.replace("'", "'\\''");
        return "sandbox-exec -p '" + escapedProfile + "' bash -c '" + escapedCmd + "'";
    }

    private String buildProfile(Path workingDir, SandboxConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("(version 1)\n");
        sb.append("(deny default)\n");

        // 进程权限
        sb.append("(allow process-exec)\n");
        sb.append("(allow process-fork)\n");
        sb.append("(allow signal)\n");
        sb.append("(allow process-info*)\n");

        // 系统调用
        sb.append("(allow sysctl-read)\n");
        sb.append("(allow mach-lookup)\n");
        sb.append("(allow ipc-posix-shm-read-data)\n");

        // 文件读取：全部允许（写入才做限制）
        sb.append("(allow file-read*)\n");

        // 必要的写入：设备文件
        sb.append("(allow file-write* (literal \"/dev/null\"))\n");
        sb.append("(allow file-write* (literal \"/dev/zero\"))\n");
        sb.append("(allow file-write* (subpath \"/dev/fd\"))\n");
        sb.append("(allow file-write* (subpath \"/dev/pts\"))\n");

        // 必要的写入：工作目录、coding-agent 数据目录、tmp
        sb.append("(allow file-write* (subpath \"").append(workingDir.toAbsolutePath()).append("\"))\n");
        String codingAgentDir = Paths.get(System.getProperty("user.home"), ".self-agent").toString();
        sb.append("(allow file-write* (subpath \"").append(codingAgentDir).append("\"))\n");
        sb.append("(allow file-write* (subpath \"/tmp\"))\n");
        sb.append("(allow file-write* (subpath \"/var/folders\"))\n");  // macOS 临时文件

        // denyWritePaths 覆盖（优先级最高，放在最后）
        for (String denied : config.denyWritePaths) {
            String expanded = denied.replace("~", System.getProperty("user.home"));
            sb.append("(deny file-write* (subpath \"").append(expanded).append("\"))\n");
        }

        // 网络
        if (config.allowNetwork) {
            sb.append("(allow network-outbound)\n");
            sb.append("(allow network-inbound)\n");
            sb.append("(allow network-bind)\n");
        }

        return sb.toString();
    }
}
