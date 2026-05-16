package com.selfagent.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PermissionGuardTest {
    @Test
    void detectsDangerousCommands() {
        assertTrue(PermissionGuard.isDangerous("rm -rf /tmp/foo"));
        assertTrue(PermissionGuard.isDangerous("git push --force origin main"));
        assertTrue(PermissionGuard.isDangerous("dd if=/dev/zero of=/dev/sda"));
        assertFalse(PermissionGuard.isDangerous("ls -la"));
        assertFalse(PermissionGuard.isDangerous("mvn compile"));
    }

    @Test
    void autoApproveSkipsNonDangerousConfirmation() {
        PermissionGuard guard = new PermissionGuard(true, System.in, System.out);
        assertTrue(guard.requestPermission("write_file", "Write to /tmp/test.txt?"));
    }

    @Test
    void autoApproveBlocksDangerousCommands() {
        PermissionGuard guard = new PermissionGuard(true, System.in, System.out);
        assertFalse(guard.requestDangerousPermission("rm -rf /"));
    }
}
