package com.selfagent.agent.multi;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentDefinitionTest {
    @Test
    void builtinGeneralPurposeHasAllTools() {
        AgentDefinition d = AgentDefinition.generalPurpose();
        assertEquals("general-purpose", d.name);
        assertNull(d.allowedTools);
        assertFalse(d.isTemporary);
    }

    @Test
    void builtinExploreHasRestrictedTools() {
        AgentDefinition d = AgentDefinition.explore();
        assertEquals("explore", d.name);
        assertNotNull(d.allowedTools);
        assertTrue(d.allowedTools.contains("read_file"));
        assertFalse(d.allowedTools.contains("write_file"));
        assertFalse(d.allowedTools.contains("Agent"));
    }

    @Test
    void temporaryAgentIsMarked() {
        AgentDefinition d = AgentDefinition.temporary("你是专家...");
        assertTrue(d.isTemporary);
        assertEquals("auto", d.name);
        assertEquals("你是专家...", d.systemPrompt);
        assertNull(d.allowedTools);
    }

    @Test
    void buildListingLineFormat() {
        AgentDefinition d = AgentDefinition.explore();
        String line = d.toListingLine();
        assertTrue(line.startsWith("- explore: "));
        assertTrue(line.contains("(Tools:"));
    }
}
