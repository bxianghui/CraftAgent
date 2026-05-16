package com.selfagent.skill;

import com.selfagent.model.ChatRequest;
import com.selfagent.model.ChatResponse;
import com.selfagent.model.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class LearnedSkillExtractor {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final LLMProvider provider;
    private final Path learnedDir;

    public record SkillCandidate(String name, String description, String content) {}

    public LearnedSkillExtractor(LLMProvider provider, Path learnedDir) {
        this.provider = provider;
        this.learnedDir = learnedDir;
    }

    public static LearnedSkillExtractor createDefault(LLMProvider provider) {
        Path dir = Paths.get(System.getProperty("user.home"), ".self-agent", "learned");
        return new LearnedSkillExtractor(provider, dir);
    }

    public void extract(List<Map<String, Object>> history, SessionTracker tracker) {
        if (!tracker.shouldExtract()) return;
        if (provider == null) return;
        try {
            String prompt = loadPrompt() + "\n\n" + formatHistory(history);
            ChatRequest req = new ChatRequest(
                List.of(Map.of("role", "user", "content", prompt)),
                List.of(), null, false, null);
            ChatResponse resp = provider.chat(req);
            if (resp.content == null || resp.content.isBlank()) return;
            SkillCandidate candidate = parseSkillJson(resp.content.trim());
            if (candidate == null) return;
            saveSkill(candidate);
            System.out.println("[Learned] New skill saved: " + candidate.name());
            System.out.println("  " + learnedDir.resolve(candidate.name()).resolve("SKILL.md"));
        } catch (Exception e) {
            System.err.println("[Learned] Extraction failed: " + e.getMessage());
        }
    }

    SkillCandidate parseSkillJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            String trimmed = json.trim();
            if (trimmed.equals("\"\"") || trimmed.isEmpty()) return null;
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start < 0 || end < 0) return null;
            JsonNode root = mapper.readTree(trimmed.substring(start, end + 1));
            String name = root.path("name").asText(null);
            String description = root.path("description").asText(null);
            String content = root.path("content").asText(null);
            if (name == null || name.isBlank() || content == null || content.isBlank()) return null;
            return new SkillCandidate(name.trim(), description != null ? description.trim() : "", content.trim());
        } catch (Exception e) {
            return null;
        }
    }

    void saveSkill(SkillCandidate candidate) throws IOException {
        Path skillDir = learnedDir.resolve(candidate.name());
        Files.createDirectories(skillDir);
        String frontmatter = "---\n" +
            "name: " + candidate.name() + "\n" +
            "description: " + candidate.description() + "\n" +
            "source: learned\n" +
            "learned_at: " + Instant.now().toString() + "\n" +
            "---\n\n";
        Files.writeString(skillDir.resolve("SKILL.md"), frontmatter + candidate.content());
    }

    private String loadPrompt() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("prompts/learn-skill.md")) {
            if (is == null) return "Analyze this conversation and extract a reusable skill if applicable.";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "Analyze this conversation and extract a reusable skill if applicable.";
        }
    }

    private String formatHistory(List<Map<String, Object>> history) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : history) {
            String role = (String) msg.get("role");
            Object content = msg.get("content");
            if (content instanceof String s && !s.isBlank()) {
                sb.append(role).append(": ").append(s, 0, Math.min(500, s.length())).append("\n");
            }
        }
        return sb.toString();
    }
}
