package com.selfagent.skill;

import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class SkillParser {
    public static SkillDefinition parse(Path skillMdPath) throws IOException {
        String raw = Files.readString(skillMdPath);
        if (!raw.startsWith("---")) {
            throw new IllegalArgumentException("SKILL.md missing frontmatter: " + skillMdPath);
        }
        int end = raw.indexOf("---", 3);
        if (end < 0) throw new IllegalArgumentException("SKILL.md unclosed frontmatter: " + skillMdPath);

        String frontmatter = raw.substring(3, end).trim();
        String prompt = raw.substring(end + 3).trim();

        Yaml yaml = new Yaml();
        Map<String, Object> fm = yaml.load(frontmatter);

        String name = (String) fm.get("name");
        String description = (String) fm.get("description");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SKILL.md missing 'name' field: " + skillMdPath);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md missing 'description' field: " + skillMdPath);
        }

        return new SkillDefinition(name, description, prompt,
            skillMdPath.toString(), skillMdPath.getParent());
    }
}
