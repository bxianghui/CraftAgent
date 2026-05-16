package com.selfagent.skill;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillManager {
    private final Path projectSkillsDir;  // <workingDir>/.self-agent/skills/
    private final Path globalSkillsDir;   // ~/.self-agent/skills/
    private final Path learnedDir;        // ~/.self-agent/learned/
    private final List<SkillDefinition> loaded = new ArrayList<>();
    private boolean dirty = true;

    public SkillManager(Path projectSkillsDir, Path globalSkillsDir) {
        this(projectSkillsDir, globalSkillsDir,
            Paths.get(System.getProperty("user.home"), ".self-agent", "learned"));
    }

    public SkillManager(Path projectSkillsDir, Path globalSkillsDir, Path learnedDir) {
        this.projectSkillsDir = projectSkillsDir;
        this.globalSkillsDir = globalSkillsDir;
        this.learnedDir = learnedDir;
    }

    public static SkillManager create(Path workingDir) {
        Path project = workingDir.resolve(".self-agent").resolve("skills");
        Path global = Paths.get(System.getProperty("user.home"), ".self-agent", "skills");
        return new SkillManager(project, global);
    }

    /** 扫描所有目录，优先级：项目级 > 全局手动 > learned（自动生成） */
    public void loadAll() {
        Map<String, SkillDefinition> byName = new LinkedHashMap<>();
        // learned 优先级最低，先加载，会被手动 skill 覆盖
        loadFromWithSource(learnedDir, byName, "learned");
        loadFrom(globalSkillsDir, byName);
        loadFrom(projectSkillsDir, byName);
        loaded.clear();
        loaded.addAll(byName.values());
        dirty = true;
    }

    public boolean consumeDirty() {
        if (dirty) { dirty = false; return true; }
        return false;
    }

    private void loadFrom(Path dir, Map<String, SkillDefinition> byName) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try {
            Files.list(dir).filter(Files::isDirectory).forEach(skillDir -> {
                Path skillMd = skillDir.resolve("SKILL.md");
                if (!Files.exists(skillMd)) return;
                try {
                    SkillDefinition skill = SkillParser.parse(skillMd);
                    byName.put(skill.name, skill);
                } catch (Exception e) {
                    System.err.println("[Skill] Failed to load " + skillMd + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("[Skill] Failed to scan " + dir + ": " + e.getMessage());
        }
    }

    private void loadFromWithSource(Path dir, Map<String, SkillDefinition> byName, String source) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try {
            Files.list(dir).filter(Files::isDirectory).forEach(skillDir -> {
                Path skillMd = skillDir.resolve("SKILL.md");
                if (!Files.exists(skillMd)) return;
                try {
                    SkillDefinition skill = SkillParser.parse(skillMd);
                    SkillDefinition withSource = new SkillDefinition(
                        skill.name, skill.description, skill.rawPrompt,
                        skill.sourcePath, skill.skillDir, source);
                    byName.put(withSource.name, withSource);
                } catch (Exception e) {
                    System.err.println("[Skill] Failed to load " + skillMd + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("[Skill] Failed to scan " + dir + ": " + e.getMessage());
        }
    }

    public String activate(String name, Path workingDir) {
        SkillDefinition skill = findByName(name);
        if (skill == null) {
            System.err.println("[Skill] Not found: " + name);
            return "Skill not found: " + name;
        }
        return SkillExecutor.execute(skill.rawPrompt,
            workingDir != null ? workingDir : skill.skillDir);
    }

    public void refresh() {
        loadAll();
        System.out.println("[Skill] Refreshed, " + loaded.size() + " skills loaded.");
    }

    public void reloadPrompt(Path skillDir) {
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) return;
        try {
            SkillDefinition updated = SkillParser.parse(skillMd);
            for (int i = 0; i < loaded.size(); i++) {
                if (loaded.get(i).skillDir.equals(skillDir)) {
                    loaded.set(i, updated);
                    System.out.println("[Skill] Prompt reloaded: " + updated.name);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[Skill] Failed to reload prompt " + skillMd + ": " + e.getMessage());
        }
    }

    public Path getProjectSkillsDir() { return projectSkillsDir; }
    public Path getGlobalSkillsDir() { return globalSkillsDir; }
    public Path getLearnedDir() { return learnedDir; }

    public boolean hasSkill(String name) { return findByName(name) != null; }
    public List<SkillDefinition> getLoaded() { return Collections.unmodifiableList(loaded); }

    private SkillDefinition findByName(String name) {
        return loaded.stream().filter(s -> s.name.equals(name)).findFirst().orElse(null);
    }
}
