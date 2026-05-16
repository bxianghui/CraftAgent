package com.selfagent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SkillParserTest {
    @Test
    void parsesFullSkillMd(@TempDir Path tmp) throws IOException {
        Path skillDir = tmp.resolve("code-reviewer");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: code-reviewer
            description: 专注于代码审查，关注安全、性能、可维护性
            ---

            你是一个资深代码审查专家。请先使用 read_file 阅读相关文件。
            """);
        SkillDefinition skill = SkillParser.parse(skillDir.resolve("SKILL.md"));
        assertEquals("code-reviewer", skill.name);
        assertEquals("专注于代码审查，关注安全、性能、可维护性", skill.description);
        assertTrue(skill.rawPrompt.contains("代码审查专家"));
        assertEquals(skillDir.resolve("SKILL.md").toString(), skill.sourcePath);
        assertEquals(skillDir, skill.skillDir);
    }

    @Test
    void parsesSkillWithBangSyntax(@TempDir Path tmp) throws IOException {
        Path skillDir = tmp.resolve("repo-analyst");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: repo-analyst
            description: 分析仓库结构
            ---

            以下是仓库信息：
            !`echo hello`
            请基于以上信息帮助用户。
            """);
        SkillDefinition skill = SkillParser.parse(skillDir.resolve("SKILL.md"));
        assertEquals("repo-analyst", skill.name);
        assertTrue(skill.rawPrompt.contains("!`echo hello`"));
    }

    @Test
    void throwsOnMissingName(@TempDir Path tmp) throws IOException {
        Path skillDir = tmp.resolve("bad-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            description: no name field
            ---
            some content
            """);
        assertThrows(IllegalArgumentException.class,
            () -> SkillParser.parse(skillDir.resolve("SKILL.md")));
    }
}
