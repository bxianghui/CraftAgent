package com.selfagent.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {
    @Test
    void loadsDefaultProvider(@TempDir Path tmp) throws IOException {
        Path cfg = tmp.resolve("config.yaml");
        Files.writeString(cfg, """
            default_provider: anthropic
            providers:
              anthropic:
                api_key: sk-ant-test
                model: claude-sonnet-4-6
            context:
              max_token_ratio: 0.8
              keep_recent_turns: 20
            """);
        AgentConfig config = AgentConfig.load(cfg);
        assertEquals("anthropic", config.defaultProvider);
        assertEquals("sk-ant-test", config.providers.get("anthropic").apiKey);
        assertEquals("claude-sonnet-4-6", config.providers.get("anthropic").model);
        assertEquals(0.8, config.context.maxTokenRatio, 0.001);
        assertEquals(20, config.context.keepRecentTurns);
    }
}
