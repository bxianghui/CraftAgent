package com.selfagent.agent.multi;

import java.util.List;

public class AgentDefinition {
    public final String name;
    public final String description;
    public final String systemPrompt;
    public final List<String> allowedTools;  // null = 全部工具
    public final String model;               // null = 继承父 agent
    public final int maxTurns;               // -1 = 不限制
    public final boolean isTemporary;

    public AgentDefinition(String name, String description, String systemPrompt,
                           List<String> allowedTools, String model, int maxTurns, boolean isTemporary) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.allowedTools = allowedTools;
        this.model = model;
        this.maxTurns = maxTurns;
        this.isTemporary = isTemporary;
    }

    public static AgentDefinition generalPurpose() {
        return new AgentDefinition("general-purpose",
            "General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks",
            null, null, null, -1, false);
    }

    public static AgentDefinition explore() {
        return new AgentDefinition("explore",
            "Fast agent specialized for exploring codebases. Use for finding files, searching keywords, or answering questions about the codebase",
            null, List.of("read_file", "list_files", "search_code", "bash", "web_fetch"), null, -1, false);
    }

    public static AgentDefinition coder() {
        return new AgentDefinition("coder",
            "Agent focused on code implementation. Writes and modifies code, runs tests and commands",
            null, List.of("read_file", "write_file", "edit_file", "bash", "list_files", "search_code"), null, -1, false);
    }

    public static AgentDefinition reviewer() {
        return new AgentDefinition("reviewer",
            "Agent for code review and verification. Reads code and reports findings without modifying files",
            null, List.of("read_file", "list_files", "search_code", "bash"), null, -1, false);
    }

    public static AgentDefinition verification() {
        String systemPrompt =
            "You are a verification agent. Your job is to verify the correctness of recent changes.\n\n" +
            "CRITICAL CONSTRAINTS:\n" +
            "- You CANNOT edit, write, or create files. Read and execute only.\n" +
            "- You MUST end your response with exactly one of: VERDICT: PASS / VERDICT: FAIL / VERDICT: PARTIAL\n\n" +
            "VERIFICATION STEPS:\n" +
            "1. Read project structure and understand what changed\n" +
            "2. Run build (if applicable): mvn compile / npm run build / etc.\n" +
            "3. Run tests: mvn test / npm test / pytest / etc.\n" +
            "4. Run at least one adversarial probe (boundary values, edge cases, error inputs)\n" +
            "5. Check for regressions in related code\n\n" +
            "For each check, output:\n" +
            "### Check: [what you're verifying]\n" +
            "**Command:** [exact command]\n" +
            "**Output:** [actual output]\n" +
            "**Result:** PASS or FAIL\n\n" +
            "VERDICT RULES:\n" +
            "- PASS: all checks pass including adversarial probes\n" +
            "- FAIL: found reproducible issues (include exact error, steps to reproduce)\n" +
            "- PARTIAL: environment limitations only (missing tools, can't start server)";
        return new AgentDefinition("verification",
            "Verify implementation correctness by running builds, tests, and adversarial probes. " +
            "Use when implementation is complete to check for bugs before finishing.",
            systemPrompt,
            List.of("read_file", "list_files", "search_code", "bash"),
            null, -1, false);
    }

    public static AgentDefinition temporary(String systemPrompt) {
        return new AgentDefinition("auto", "Temporary agent", systemPrompt, null, null, -1, true);
    }

    public String toListingLine() {
        String tools = allowedTools == null ? "All tools" : String.join(", ", allowedTools);
        return "- " + name + ": " + description + " (Tools: " + tools + ")";
    }
}
