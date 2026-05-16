package com.selfagent.selfcheck;

import com.selfagent.agent.AgentContext;
import com.selfagent.agent.ContextManager;
import com.selfagent.agent.multi.AgentOrchestrator;
import com.selfagent.agent.multi.SubAgentResult;
import com.selfagent.agent.multi.SubAgentTask;

public class SelfcheckManager {
    private static final String DEFAULT_PROMPT =
        "Review the recent changes in this session and verify they are correct.\n" +
        "Steps:\n" +
        "1. Identify what files were modified based on our conversation\n" +
        "2. Run the project's build and test suite\n" +
        "3. Execute at least one adversarial probe (edge cases, boundary values)\n" +
        "4. Report findings with VERDICT: PASS / VERDICT: FAIL / VERDICT: PARTIAL";

    private final AgentOrchestrator orchestrator;
    private final VerificationTrigger trigger = new VerificationTrigger();

    public SelfcheckManager(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public boolean incrementAndCheck() {
        return trigger.increment();
    }

    public void reset() {
        trigger.reset();
    }

    public int getCount() { return trigger.getCount(); }

    public VerdictParser.Verdict runVerification(String prompt, AgentContext ctx, ContextManager cm) {
        if (orchestrator == null) return VerdictParser.Verdict.UNKNOWN;
        String effectivePrompt = (prompt != null && !prompt.isBlank()) ? prompt : DEFAULT_PROMPT;
        SubAgentTask task = new SubAgentTask("verification", effectivePrompt,
            "verifying changes", null, false);
        SubAgentResult result = orchestrator.run(task, ctx);
        VerdictParser.Verdict verdict = VerdictParser.parse(result.result());

        String color = switch (verdict) {
            case PASS    -> "\033[32m";
            case FAIL    -> "\033[31m";
            case PARTIAL -> "\033[33m";
            default      -> "\033[90m";
        };
        System.out.println(color + "VERDICT: " + verdict.name() + "\033[0m");

        // 通知 SessionTracker，PASS 时标记以触发 learned skill 提炼
        if (verdict == VerdictParser.Verdict.PASS && ctx != null && ctx.sessionTracker != null) {
            ctx.sessionTracker.onSelfcheckPass();
        }

        if (verdict == VerdictParser.Verdict.FAIL && cm != null) {
            String notification = VerdictParser.formatTaskNotification(result.result(), verdict);
            cm.addUserMessage(notification);
            System.out.println("\033[90m[Selfcheck] Failure injected to context for auto-fix.\033[0m");
        }
        return verdict;
    }
}
