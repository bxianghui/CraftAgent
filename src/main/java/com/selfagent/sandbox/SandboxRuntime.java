package com.selfagent.sandbox;

import java.nio.file.Path;

public interface SandboxRuntime {
    String wrap(String cmd, Path workingDir, SandboxConfig config);
    boolean isSupported();

    static SandboxRuntime detect() {
        MacOSSandboxRuntime macos = new MacOSSandboxRuntime();
        if (macos.isSupported()) return macos;
        LinuxSandboxRuntime linux = new LinuxSandboxRuntime();
        if (linux.isSupported()) return linux;
        return new FallbackSandboxRuntime();
    }
}
