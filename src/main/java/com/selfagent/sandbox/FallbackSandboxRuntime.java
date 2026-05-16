package com.selfagent.sandbox;

import java.nio.file.Path;

public class FallbackSandboxRuntime implements SandboxRuntime {

    @Override
    public boolean isSupported() { return true; }

    @Override
    public String wrap(String cmd, Path workingDir, SandboxConfig config) {
        // 不做 OS 级包装，路径校验在 ExecutionContext.isWriteAllowed() 中处理
        return cmd;
    }
}
