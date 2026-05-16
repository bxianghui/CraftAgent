package com.selfagent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SkillManagerTest {
    private void createSkill(Path root, String name, String description, String prompt) throws IOException {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"),
            "---\nname: " + name + "\ndescription: " + description + "\n---\n\n" + prompt);
    }

    @Test
    void loadsSkillsFromDirectory(@TempDir Path tmp) throws IOException {
        createSkill(tmp, "code-reviewer", "代码审查专家", "你是代码审查专家");
        createSkill(tmp, "java-expert", "Java专家模式", "你是Java专家");
        SkillManager mgr = new SkillManager(null, tmp);
        mgr.loadAll();
        assertEquals(2, mgr.getLoaded().size());
    }

    @Test
    void projectSkillOverridesGlobal(@TempDir Path projectSkills, @TempDir Path globalSkills) throws IOException {
        createSkill(globalSkills, "code-reviewer", "全局版本", "全局 prompt");
        createSkill(projectSkills, "code-reviewer", "项目版本", "项目 prompt");
        SkillManager mgr = new SkillManager(projectSkills, globalSkills);
        mgr.loadAll();
        assertEquals(1, mgr.getLoaded().size());
        assertEquals("项目版本", mgr.getLoaded().get(0).description);
    }

    @Test
    void activateReturnsSkillPrompt(@TempDir Path tmp) throws IOException {
        createSkill(tmp, "code-reviewer", "代码审查", "你是代码审查专家");
        SkillManager mgr = new SkillManager(null, tmp);
        mgr.loadAll();
        String result = mgr.activate("code-reviewer", tmp);
        assertTrue(result.contains("代码审查专家"));
    }

    @Test
    void activateUnknownSkillReturnsError(@TempDir Path tmp) throws IOException {
        SkillManager mgr = new SkillManager(null, tmp);
        mgr.loadAll();
        String result = mgr.activate("nonexistent", tmp);
        assertTrue(result.startsWith("Skill not found"));
    }

    @Test
    void hasSkillReturnsTrueForLoadedSkill(@TempDir Path tmp) throws IOException {
        createSkill(tmp, "code-reviewer", "代码审查", "你是代码审查专家");
        SkillManager mgr = new SkillManager(null, tmp);
        mgr.loadAll();
        assertTrue(mgr.hasSkill("code-reviewer"));
        assertFalse(mgr.hasSkill("nonexistent"));
    }

    @Test
    void loadsLearnedSkills(@TempDir Path globalSkills, @TempDir Path learnedDir) throws IOException {
        createSkill(learnedDir, "auto-fix", "自动修复", "自动修复步骤");
        SkillManager mgr = new SkillManager(null, globalSkills, learnedDir);
        mgr.loadAll();
        assertEquals(1, mgr.getLoaded().size());
        assertEquals("learned", mgr.getLoaded().get(0).source);
    }

    @Test
    void manualSkillOverridesLearned(@TempDir Path globalSkills, @TempDir Path learnedDir) throws IOException {
        createSkill(learnedDir, "my-skill", "learned version", "learned content");
        createSkill(globalSkills, "my-skill", "manual version", "manual content");
        SkillManager mgr = new SkillManager(null, globalSkills, learnedDir);
        mgr.loadAll();
        assertEquals(1, mgr.getLoaded().size());
        assertEquals("manual version", mgr.getLoaded().get(0).description);
    }
}
