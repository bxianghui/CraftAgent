package com.selfagent.skill;

import java.nio.file.Path;

public class SkillDefinition {
    public final String name;
    public final String description;
    public final String rawPrompt;
    public final String sourcePath;
    public final Path skillDir;
    public final String source;  // "manual" 或 "learned"

    public SkillDefinition(String name, String description, String rawPrompt,
                           String sourcePath, Path skillDir) {
        this(name, description, rawPrompt, sourcePath, skillDir, "manual");
    }

    public SkillDefinition(String name, String description, String rawPrompt,
                           String sourcePath, Path skillDir, String source) {
        this.name = name;
        this.description = description;
        this.rawPrompt = rawPrompt;
        this.sourcePath = sourcePath;
        this.skillDir = skillDir;
        this.source = source != null ? source : "manual";
    }
}
