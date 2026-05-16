package com.selfagent.agent.multi;

import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class AgentRegistry {
    private final Map<String, AgentDefinition> agents = new LinkedHashMap<>();
    private boolean dirty = true;

    private AgentRegistry() {}

    public static AgentRegistry create(Path workingDir) {
        AgentRegistry reg = new AgentRegistry();
        for (AgentDefinition d : List.of(
                AgentDefinition.generalPurpose(), AgentDefinition.explore(),
                AgentDefinition.coder(), AgentDefinition.reviewer(),
                AgentDefinition.verification())) {
            reg.agents.put(d.name, d);
        }
        Path global = Paths.get(System.getProperty("user.home"), ".self-agent", "agents");
        reg.loadFrom(global);
        if (workingDir != null) {
            reg.loadFrom(workingDir.resolve(".self-agent").resolve("agents"));
        }
        return reg;
    }

    private void loadFrom(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try {
            Files.list(dir)
                .filter(p -> p.toString().endsWith(".md"))
                .forEach(p -> {
                    try {
                        AgentDefinition def = parseAgentFile(p);
                        if (def != null) agents.put(def.name, def);
                    } catch (Exception e) {
                        System.err.println("[AgentRegistry] Failed to load " + p + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("[AgentRegistry] Failed to scan " + dir + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private AgentDefinition parseAgentFile(Path path) throws IOException {
        String raw = Files.readString(path);
        if (!raw.startsWith("---")) return null;
        int end = raw.indexOf("---", 3);
        if (end < 0) return null;
        String frontmatter = raw.substring(3, end).trim();
        String prompt = raw.substring(end + 3).trim();
        Yaml yaml = new Yaml();
        Map<String, Object> fm = yaml.load(frontmatter);
        String name = (String) fm.get("name");
        String description = (String) fm.getOrDefault("description", "");
        String model = (String) fm.get("model");
        int maxTurns = fm.containsKey("max_turns") ? ((Number) fm.get("max_turns")).intValue() : -1;
        List<String> tools = fm.containsKey("tools") ? (List<String>) fm.get("tools") : null;
        if (name == null || name.isBlank()) return null;
        return new AgentDefinition(name, description, prompt, tools, model, maxTurns, false);
    }

    public AgentDefinition find(String name) { return agents.get(name); }

    public AgentDefinition createTemporary(String systemPrompt) {
        return AgentDefinition.temporary(systemPrompt);
    }

    public List<AgentDefinition> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(agents.values()));
    }

    public boolean consumeDirty() {
        if (dirty) { dirty = false; return true; }
        return false;
    }

    public String buildAgentReminder() {
        StringBuilder sb = new StringBuilder("Available agent types for the Agent tool:\n\n");
        agents.values().forEach(d -> sb.append(d.toListingLine()).append("\n"));
        sb.append("\nLaunch multiple agents concurrently when possible using run_in_background=true.");
        return sb.toString().trim();
    }
}
