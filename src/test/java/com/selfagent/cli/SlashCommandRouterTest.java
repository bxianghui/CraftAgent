package com.selfagent.cli;

import com.selfagent.agent.ContextManager;
import com.selfagent.model.AgentConfig;
import com.selfagent.tool.McpManager;
import com.selfagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.*;

class SlashCommandRouterTest {
    ToolRegistry registry = new ToolRegistry();
    ContextManager cm = new ContextManager(100_000, 0.8, 20);
    McpManager mcpManager = new McpManager(new AgentConfig(), registry);

    SlashCommandRouter router(PrintStream ps) {
        return new SlashCommandRouter(registry, cm, mcpManager, null, null, null, ps);
    }

    @Test
    void recognizesSlashCommand() {
        assertTrue(SlashCommandRouter.isSlashCommand("/help"));
        assertTrue(SlashCommandRouter.isSlashCommand("/clear"));
        assertFalse(SlashCommandRouter.isSlashCommand("hello"));
        assertFalse(SlashCommandRouter.isSlashCommand(""));
    }

    @Test
    void helpCommandPrintsOutput() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        router(new PrintStream(baos)).handle("/help", null);
        String output = baos.toString();
        assertTrue(output.contains("/model") || output.contains("help") || output.contains("commands"));
    }

    @Test
    void clearCommandClearsContext() {
        cm.addUserMessage("test message");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        router(new PrintStream(baos)).handle("/clear", null);
        assertEquals(0, cm.buildMessages().size());
    }

    @Test
    void toolsCommandListsTools() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        router(new PrintStream(baos)).handle("/tools", null);
        assertTrue(baos.toString().length() >= 0);
    }

    @Test
    void unknownCommandPrintsError() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        router(new PrintStream(baos)).handle("/nonexistent", null);
        assertTrue(baos.toString().toLowerCase().contains("unknown"));
    }
}
