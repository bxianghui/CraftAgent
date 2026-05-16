package com.selfagent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LearnedSkillExtractorTest {
    @Test
    void parseSkillJsonReturnsSkill() {
        LearnedSkillExtractor e = new LearnedSkillExtractor(null, Path.of("/tmp"));
        LearnedSkillExtractor.SkillCandidate c = e.parseSkillJson(
            "{\"name\":\"fix-sandbox\",\"description\":\"修复沙箱权限\",\"content\":\"步骤1...\"}");
        assertNotNull(c);
        assertEquals("fix-sandbox", c.name());
        assertEquals("修复沙箱权限", c.description());
        assertTrue(c.content().contains("步骤1"));
    }

    @Test
    void parseSkillJsonReturnsNullForEmpty() {
        LearnedSkillExtractor e = new LearnedSkillExtractor(null, Path.of("/tmp"));
        assertNull(e.parseSkillJson("\"\""));
        assertNull(e.parseSkillJson(""));
        assertNull(e.parseSkillJson(null));
    }

    @Test
    void buildSkillFileSavesCorrectly(@TempDir Path tmp) throws Exception {
        LearnedSkillExtractor e = new LearnedSkillExtractor(null, tmp);
        LearnedSkillExtractor.SkillCandidate c =
            new LearnedSkillExtractor.SkillCandidate("my-skill", "描述", "正文内容");
        e.saveSkill(c);
        Path skillFile = tmp.resolve("my-skill/SKILL.md");
        assertTrue(Files.exists(skillFile));
        String content = Files.readString(skillFile);
        assertTrue(content.contains("name: my-skill"));
        assertTrue(content.contains("source: learned"));
        assertTrue(content.contains("正文内容"));
    }
}
