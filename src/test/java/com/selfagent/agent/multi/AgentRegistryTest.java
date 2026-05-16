package com.selfagent.agent.multi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {
    @Test
    void builtinAgentsAlwaysPresent(@TempDir Path tmp) {
        AgentRegistry reg = AgentRegistry.create(tmp);
        assertNotNull(reg.find("general-purpose"));
        assertNotNull(reg.find("explore"));
        assertNotNull(reg.find("coder"));
        assertNotNull(reg.find("reviewer"));
        assertNotNull(reg.find("verification"));
    }

    @Test
    void loadsCustomAgentFromFile(@TempDir Path tmp) throws IOException {
        Path agentDir = tmp.resolve(".self-agent/agents");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("my-agent.md"),
            "---\nname: my-agent\ndescription: 自定义 agent\n---\n\n你是专家");
        AgentRegistry reg = AgentRegistry.create(tmp);
        AgentDefinition def = reg.find("my-agent");
        assertNotNull(def);
        assertEquals("自定义 agent", def.description);
        assertEquals("你是专家", def.systemPrompt);
    }

    @Test
    void projectAgentOverridesBuiltin(@TempDir Path tmp) throws IOException {
        Path agentDir = tmp.resolve(".self-agent/agents");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("explore.md"),
            "---\nname: explore\ndescription: 项目级 explore\n---\n\n自定义 explore");
        AgentRegistry reg = AgentRegistry.create(tmp);
        assertEquals("项目级 explore", reg.find("explore").description);
    }

    @Test
    void temporaryAgentCreation(@TempDir Path tmp) {
        AgentRegistry reg = AgentRegistry.create(tmp);
        AgentDefinition def = reg.createTemporary("你是临时专家");
        assertTrue(def.isTemporary);
        assertEquals("auto", def.name);
    }

    @Test
    void consumeDirtyReturnsTrueOnce(@TempDir Path tmp) {
        AgentRegistry reg = AgentRegistry.create(tmp);
        assertTrue(reg.consumeDirty());
        assertFalse(reg.consumeDirty());
    }
}
