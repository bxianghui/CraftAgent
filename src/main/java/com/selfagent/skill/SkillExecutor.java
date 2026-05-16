package com.selfagent.skill;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行 SKILL.md 正文中的 ! 语法：
 *   !`command` — 激活时框架执行命令，输出替换原占位符，模型只看结果。
 * 命令在 skill 目录下执行，超时 30 秒，失败时保留原占位符并打印警告。
 */
public class SkillExecutor {
    private static final Pattern BANG_PATTERN = Pattern.compile("!`([^`]+)`");
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * 处理 rawPrompt，执行所有 !`command` 并用输出替换，返回最终 prompt 文本。
     */
    public static String execute(String rawPrompt, Path workingDir) {
        Matcher m = BANG_PATTERN.matcher(rawPrompt);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String cmd = m.group(1).trim();
            String output = runCommand(cmd, workingDir);
            m.appendReplacement(sb, Matcher.quoteReplacement(output));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String runCommand(String cmd, Path workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            if (workingDir != null) pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                System.err.println("[Skill] Command timed out: " + cmd);
                return "!`" + cmd + "`";
            }
            return out.toString().trim();
        } catch (Exception e) {
            System.err.println("[Skill] Command failed: " + cmd + " — " + e.getMessage());
            return "!`" + cmd + "`";
        }
    }
}
