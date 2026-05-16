package com.selfagent.tool;

import com.selfagent.sandbox.SandboxConfig;
import com.selfagent.sandbox.SandboxRuntime;
import org.jline.reader.LineReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ExecutionContext {
    public final Path workingDir;
    public final boolean autoApprove;
    public final PrintStream out;
    public final SandboxConfig sandboxConfig;
    public final SandboxRuntime sandboxRuntime;
    public final LineReader lineReader;
    public final PrintWriter terminalWriter;

    public ExecutionContext(Path workingDir, boolean autoApprove, PrintStream out) {
        this(workingDir, autoApprove, out, null, null, null, null);
    }

    public ExecutionContext(Path workingDir, boolean autoApprove, PrintStream out, SandboxConfig sandboxConfig) {
        this(workingDir, autoApprove, out, sandboxConfig, null, null, null);
    }

    public ExecutionContext(Path workingDir, boolean autoApprove, PrintStream out,
                            SandboxConfig sandboxConfig, LineReader lineReader, PrintWriter terminalWriter) {
        this(workingDir, autoApprove, out, sandboxConfig, null, lineReader, terminalWriter);
    }

    public ExecutionContext(Path workingDir, boolean autoApprove, PrintStream out,
                            SandboxConfig sandboxConfig, SandboxRuntime sandboxRuntime,
                            LineReader lineReader, PrintWriter terminalWriter) {
        this.workingDir = workingDir;
        this.autoApprove = autoApprove;
        this.out = out;
        this.sandboxConfig = sandboxConfig;
        this.sandboxRuntime = sandboxRuntime;
        this.lineReader = lineReader;
        this.terminalWriter = terminalWriter;
    }

    public boolean isWriteAllowed(Path path) {
        if (sandboxConfig == null || !sandboxConfig.enabled) return true;
        Path abs = path.toAbsolutePath().normalize();
        Path codingAgentDir = Paths.get(System.getProperty("user.home"), ".self-agent").normalize();
        // deny_write_paths 优先
        for (String denied : sandboxConfig.denyWritePaths) {
            Path deniedPath = Paths.get(denied.replace("~", System.getProperty("user.home"))).normalize();
            if (abs.startsWith(deniedPath)) return false;
        }
        // 允许 workingDir 和 ~/.self-agent
        if (abs.startsWith(workingDir.toAbsolutePath().normalize())) return true;
        if (abs.startsWith(codingAgentDir)) return true;
        return true; // 方案3：其他路径默认允许，只有 denyWritePaths 中的路径拒绝
    }
}
