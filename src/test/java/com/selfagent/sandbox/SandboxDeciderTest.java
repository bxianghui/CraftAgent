package com.selfagent.sandbox;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SandboxDeciderTest {
    private final SandboxConfig config = new SandboxConfig();
    private final SandboxDecider decider = new SandboxDecider();

    @Test
    void blocksDangerousCommands() {
        // 文件系统破坏
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("rm -rf /", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("rm -rf /tmp/test", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("mkfs.ext4 /dev/sda", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("dd if=/dev/zero of=/dev/sda", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("shred /dev/sda", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("echo bad > /dev/sda", config));
        // 权限破坏
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("chmod -R 777 /etc", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("chown -R root /usr", config));
        // 进程破坏
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("kill -9 1", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("kill -KILL 1", config));
        // git 危险操作
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("git push --force", config));
        // fork bomb
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide(":(){ :|:& };:", config));
        // 系统配置破坏
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("mv /etc/passwd /tmp/passwd.bak", config));
    }

    @Test
    void blocksRegardlessOfSandboxEnabled() {
        // 黑名单永远生效，不受 enabled 开关影响
        SandboxConfig disabled = new SandboxConfig();
        disabled.enabled = false;
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("rm -rf /tmp/test", disabled));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("dd if=/dev/zero of=/dev/null bs=1M count=1", disabled));
    }

    @Test
    void allowsWhitelistedCommands() {
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("git status", config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("mvn clean package", config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("npm install", config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("node --version", config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("python --version", config));
    }

    @Test
    void requiresApprovalForUnknownCommands() {
        // curl/wget 不在白名单，需要审批
        assertEquals(SandboxDecider.Decision.REQUIRE_APPROVAL, decider.decide("curl http://example.com", config));
        assertEquals(SandboxDecider.Decision.REQUIRE_APPROVAL, decider.decide("wget http://example.com", config));
        // echo/ls 在白名单（只读查看命令），直接放行
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("echo hello", config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("ls -la", config));
    }

    @Test
    void customAllowCommandsWork() {
        SandboxConfig custom = new SandboxConfig();
        custom.allowCommands = List.of("curl *");
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("curl http://example.com", custom));
        // 不在自定义白名单里的仍需审批
        assertEquals(SandboxDecider.Decision.REQUIRE_APPROVAL, decider.decide("wget http://example.com", custom));
    }

    @Test
    void autoApproveDoesNotBypassBlockList() {
        // --yes (autoApprove) 只跳过审批，黑名单永远不可绕过
        // SandboxDecider 不感知 autoApprove，BLOCK 决策在 ApprovalGate 调用之前返回
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("rm -rf /tmp/test", config));
        assertEquals(SandboxDecider.Decision.BLOCK, decider.decide("mkfs.ext4 /dev/sda", config));
        // ALLOW 和 REQUIRE_APPROVAL 才会到达 ApprovalGate
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("git status", config));
        // echo 在白名单，直接 ALLOW（不经过 ApprovalGate）
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("echo hello", config));
        // curl 不在白名单，需要 REQUIRE_APPROVAL
        assertEquals(SandboxDecider.Decision.REQUIRE_APPROVAL, decider.decide("curl http://example.com", config));
    }

    @Test
    void nullAndBlankCommandsAreAllowed() {
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide(null, config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("", config));
        assertEquals(SandboxDecider.Decision.ALLOW, decider.decide("   ", config));
    }
}
