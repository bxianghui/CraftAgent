package com.selfagent.sandbox;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LinuxSandboxRuntime implements SandboxRuntime {

    @Override
    public boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return false;
        try {
            Process p = new ProcessBuilder("which", "bwrap").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String wrap(String cmd, Path workingDir, SandboxConfig config) {
        List<String> bwrapArgs = new ArrayList<>();
        bwrapArgs.add("bwrap");
        bwrapArgs.add("--ro-bind"); bwrapArgs.add("/"); bwrapArgs.add("/");
        String wd = workingDir.toAbsolutePath().toString();
        bwrapArgs.add("--bind"); bwrapArgs.add(wd); bwrapArgs.add(wd);
        String codingAgentDir = Paths.get(System.getProperty("user.home"), ".self-agent").toString();
        bwrapArgs.add("--bind"); bwrapArgs.add(codingAgentDir); bwrapArgs.add(codingAgentDir);
        bwrapArgs.add("--dev"); bwrapArgs.add("/dev");
        bwrapArgs.add("--proc"); bwrapArgs.add("/proc");
        bwrapArgs.add("--tmpfs"); bwrapArgs.add("/tmp");
        if (!config.allowNetwork) {
            bwrapArgs.add("--unshare-net");
        }
        bwrapArgs.add("bash"); bwrapArgs.add("-c"); bwrapArgs.add(cmd);
        return String.join(" ", bwrapArgs.stream()
            .map(a -> a.contains(" ") ? "\"" + a + "\"" : a)
            .toList());
    }
}
