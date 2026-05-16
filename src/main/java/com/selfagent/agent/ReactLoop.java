package com.selfagent.agent;

import com.selfagent.common.Timed;
import com.selfagent.common.ThinkingLogger;
import com.selfagent.common.TimingLogger;
import com.selfagent.common.ToolDisplay;
import com.selfagent.history.HistoryEvent;
import com.selfagent.memory.MemoryExtractor;
import com.selfagent.memory.MemoryInjector;
import com.selfagent.model.*;
import com.selfagent.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ReactLoop {
    private final ToolRegistry toolRegistry;
    private final ContextManager contextManager;
    private final MemoryInjector memoryInjector;
    private final ObjectMapper mapper = new ObjectMapper();
    private com.selfagent.skill.SkillManager currentSkillManager;

    public ReactLoop(ToolRegistry toolRegistry, ContextManager contextManager) {
        this(toolRegistry, contextManager, null, null);
    }

    public ReactLoop(ToolRegistry toolRegistry, ContextManager contextManager,
                     MemoryInjector memoryInjector, MemoryExtractor memoryExtractor) {
        this.toolRegistry = toolRegistry;
        this.contextManager = contextManager;
        this.memoryInjector = memoryInjector;
    }

    /** 非流式运行，返回完整文本 */
    @Timed("run — 完整非流式对话轮次")
    public String run(String userInput, AgentContext ctx) {
        long t0 = System.currentTimeMillis();
        prepareRun(userInput, ctx);
        TimingLogger.log("prepareRun", t0);

        int round = 0;
        while (true) {
            round++;
            long tr = System.currentTimeMillis();
            String result;
            try {
                result = executeNonStreamRound(ctx);
            } catch (Exception e) {
                String errMsg = "LLM request failed: " + e.getMessage() + ". Please check your network or API configuration and try again.";
                System.err.println("[ReactLoop] run error round-" + round + ": " + e.getMessage());
                return errMsg;
            }
            TimingLogger.log("round-" + round, tr);
            if (result != null) return result;
        }
    }

    /** 流式运行：每轮均走流式，无轮次上限。 */
    @Timed("streamRun — 流式对话轮次")
    public String streamRun(String userInput, AgentContext ctx, Consumer<String> onChunk) {
        return streamRun(userInput, ctx, onChunk, null, null);
    }

    public String streamRun(String userInput, AgentContext ctx, Consumer<String> onChunk,
                             Runnable onWaitStart, Runnable onWaitEnd) {
        long t0 = System.currentTimeMillis();
        prepareRun(userInput, ctx);
        TimingLogger.log("prepareRun", t0);

        int round = 0;
        while (true) {
            round++;
            long tr = System.currentTimeMillis();

            long tb = System.currentTimeMillis();
            ChatRequest req = new ChatRequest(contextManager.buildMessages(), buildToolDefs(), null, false, contextManager.buildSystemPrompt());
            TimingLogger.log("buildRequest-" + round, tb);

            ctx.sessionLogger.logRequest(ctx.sessionId, req);

            long ts = System.currentTimeMillis();
            StreamResult sr;
            // 第2轮起（工具调用后）启动等待动效
            final int r = round;
            if (r > 1 && onWaitStart != null) onWaitStart.run();
            try {
                sr = collectStream(ctx.provider.stream(req), chunk -> {
                    if (r > 1 && onWaitEnd != null) onWaitEnd.run();
                    if (onChunk != null) onChunk.accept(chunk);
                });
            } catch (Exception e) {
                if (r > 1 && onWaitEnd != null) onWaitEnd.run();
                String errMsg = "LLM request failed: " + e.getMessage() + ". Please check your network or API configuration and try again.";
                System.err.println("[ReactLoop] stream error round-" + round + ": " + e.getMessage());
                if (onChunk != null) onChunk.accept(errMsg);
                return errMsg;
            }
            // 正文末尾无换行，补一个
            System.out.println();
            // thinking 聚合后打印（灰色），在正文换行之后
            if (sr.thinkingContent != null) {
                if (ThinkingLogger.isEnabled()) {
                    ToolDisplay.thinkingPrefix();
                    System.out.println("\033[90m" + sr.thinkingContent + "\033[0m");
                }
                silentAppend(ctx, HistoryEvent.thinking(ctx.sessionId, sr.thinkingContent));
            }
            TimingLogger.log("stream-" + round, ts);

            if (sr.toolCalls.isEmpty()) {
                String result = finishRound(sr.content, ctx);
                TimingLogger.log("total-round-" + round, tr);
                return result;
            }

            contextManager.addAssistantMessage(sr.content, sr.toolCalls);

            long tt = System.currentTimeMillis();
            executeToolCalls(sr.toolCalls, ctx);
            TimingLogger.log("toolCalls-" + round + "(" + sr.toolCalls.size() + ")", tt);

            TimingLogger.log("total-round-" + round, tr);
        }
    }

    /** 注入记忆、检查压缩、记录用户输入、添加到历史 — run/streamRun 共用的前置逻辑 */
    private void prepareRun(String userInput, AgentContext ctx) {
        long t0 = System.currentTimeMillis();

        if (contextManager.needsCompression()) {
            long tc = System.currentTimeMillis();
            contextManager.compress(ctx.provider);
            TimingLogger.log("compress", tc);
        }

        if (memoryInjector != null) {
            long tm = System.currentTimeMillis();
            contextManager.setSystemPromptSuffix(memoryInjector.buildMemoryBlock(userInput));
            TimingLogger.log("memorySearch", tm);
        }

        // SessionTracker 记录用户输入
        if (ctx.sessionTracker != null) ctx.sessionTracker.onUserMessage(userInput);

        currentSkillManager = ctx.skillManager;
        silentAppend(ctx, HistoryEvent.userInput(ctx.sessionId, userInput));

        List<String> reminders = new java.util.ArrayList<>();
        if (ctx.skillManager != null && ctx.skillManager.consumeDirty() && !ctx.skillManager.getLoaded().isEmpty()) {
            reminders.add(buildSkillReminder(ctx.skillManager));
        }
        // agent 列表已内嵌在 Agent tool description 里，无需单独 reminder
        // 检查已完成的后台 agent，注入 task-notification
        String bgNotification = collectCompletedBackgroundAgents(ctx);
        if (bgNotification != null) reminders.add(bgNotification);

        if (!reminders.isEmpty()) {
            contextManager.addUserMessageWithReminders(userInput, reminders);
        } else {
            contextManager.addUserMessage(userInput);
        }

        TimingLogger.log("prepareRun-inner", t0);
    }

    /**
     * 执行一轮非流式 LLM 调用：
     * - 无工具调用 → 返回最终文本（完成）
     * - 有工具调用 → 执行工具，返回 null（需继续下一轮）
     */
    private String executeNonStreamRound(AgentContext ctx) {
        ChatRequest req = new ChatRequest(contextManager.buildMessages(), buildToolDefs(), null, false, contextManager.buildSystemPrompt());
        ctx.sessionLogger.logRequest(ctx.sessionId, req);
        ChatResponse resp = ctx.provider.chat(req);

        if (resp.thinkingContent != null) {
            silentAppend(ctx, HistoryEvent.thinking(ctx.sessionId, resp.thinkingContent));
        }

        if (!resp.hasToolCalls()) {
            return finishRound(resp.content, ctx);
        }

        contextManager.addAssistantMessage(resp.content, resp.toolCalls);
        executeToolCalls(resp.toolCalls, ctx);
        return null;
    }

    /** 记录最终文本、提取短时记忆 — 轮次结束的共用收尾逻辑 */
    private String finishRound(String content, AgentContext ctx) {
        // 空内容不写入 history，避免破坏后续对话上下文
        if (content != null && !content.isBlank()) {
            contextManager.addAssistantMessage(content, List.of());
            silentAppend(ctx, HistoryEvent.assistantText(ctx.sessionId, content));
        }
        return content;
    }

    /** 收集流式 chunks，纯文本时通过 onChunk 回调，同时拼接工具调用 */
    private StreamResult collectStream(Stream<ChatChunk> chunks, Consumer<String> onChunk) {
        StringBuilder content = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        Map<String, StringBuilder> toolArgsMap = new LinkedHashMap<>();
        Map<String, String> toolNameMap = new LinkedHashMap<>();

        chunks.forEach(chunk -> {
            if (chunk.done) return;
            if (chunk.deltaContent != null) {
                content.append(chunk.deltaContent);
                if (onChunk != null) onChunk.accept(chunk.deltaContent);
            }
            if (chunk.thinkingDelta != null) {
                thinkingContent.append(chunk.thinkingDelta);
            }
            if (chunk.toolCallId != null && chunk.toolCallName != null) {
                toolNameMap.put(chunk.toolCallId, chunk.toolCallName);
                toolArgsMap.putIfAbsent(chunk.toolCallId, new StringBuilder());
            }
            if (chunk.toolCallArgsDelta != null && chunk.toolCallId != null) {
                toolArgsMap.computeIfAbsent(chunk.toolCallId, k -> new StringBuilder())
                    .append(chunk.toolCallArgsDelta);
            }
        });

        List<ToolCall> toolCalls = new ArrayList<>();
        toolNameMap.forEach((id, name) -> {
            String argsJson = toolArgsMap.getOrDefault(id, new StringBuilder()).toString();
            try {
                Map<String, Object> args = argsJson.isBlank()
                    ? Map.of()
                    : mapper.readValue(argsJson, Map.class);
                toolCalls.add(new ToolCall(id, name, args));
            } catch (Exception e) {
                toolCalls.add(new ToolCall(id, name, Map.of("_raw", argsJson)));
            }
        });

        return new StreamResult(
            content.isEmpty() ? null : content.toString(),
            thinkingContent.isEmpty() ? null : thinkingContent.toString(),
            toolCalls
        );
    }

    private void executeToolCalls(List<ToolCall> toolCalls, AgentContext ctx) {
        for (ToolCall tc : toolCalls) {
            silentAppend(ctx, HistoryEvent.toolCall(ctx.sessionId, tc.id, tc.name, tc.arguments));


            if ("Skill".equals(tc.name) && ctx.skillManager != null) {
                String skillName = (String) tc.arguments.get("skill");
                String args = tc.arguments.get("args") instanceof String s ? s : "";
                if (skillName != null && ctx.skillManager.hasSkill(skillName)) {
                    long skillStart = System.currentTimeMillis();
                    ToolDisplay.skillStart(skillName);
                    String prompt = ctx.skillManager.activate(skillName, ctx.workingDir);
                    if (!args.isBlank()) prompt = prompt.replace("$ARGUMENTS", args);
                    ToolDisplay.skillSuccess(skillName, System.currentTimeMillis() - skillStart);
                    silentAppend(ctx, HistoryEvent.toolResult(ctx.sessionId, tc.id, tc.name, "", false, 0));
                    contextManager.addToolResult(tc.id, "");
                    contextManager.addUserMessage(prompt);
                    silentAppend(ctx, HistoryEvent.userInput(ctx.sessionId, "[skill:" + skillName + "] " + prompt));
                } else {
                    String errMsg = "Skill not found: " + skillName + ". Proceed to answer the user's request directly.";
                    ToolDisplay.failure("Skill", tc.arguments, "not found: " + skillName);
                    silentAppend(ctx, HistoryEvent.toolResult(ctx.sessionId, tc.id, tc.name, errMsg, false, 0));
                    contextManager.addToolResult(tc.id, errMsg);
                }
                continue;
            }

            // PreToolUse hook
            java.util.Map<String, Object> effectiveArgs = tc.arguments;
            if (ctx.hookManager != null && ctx.hookManager.hasHooks("PreToolUse")) {
                com.selfagent.hook.HookInput preInput = new com.selfagent.hook.HookInput(
                    "PreToolUse", ctx.sessionId, tc.name, tc.arguments,
                    null, false, null, null,
                    ctx.workingDir != null ? ctx.workingDir.toString() : "");
                com.selfagent.hook.HookOutput preOut = ctx.hookManager.fire("PreToolUse", preInput);
                if (!preOut.continueExecution() || "deny".equals(preOut.permissionDecision())) {
                    String reason = preOut.stopReason() != null ? preOut.stopReason() : "Blocked by hook";
                    ToolDisplay.failure(tc.name, tc.arguments, "hook blocked");
                    String msg = "Tool execution blocked by hook: " + reason +
                        "\n[Inform the user and suggest an alternative approach.]";
                    silentAppend(ctx, HistoryEvent.toolResult(ctx.sessionId, tc.id, tc.name, msg, true, 0));
                    contextManager.addToolResult(tc.id, msg);
                    if (preOut.additionalContext() != null) {
                        contextManager.addUserMessage("<system-reminder>\n" + preOut.additionalContext() + "\n</system-reminder>");
                    }
                    continue;
                }
                if (preOut.updatedInput() != null) effectiveArgs = preOut.updatedInput();
                if (preOut.additionalContext() != null) {
                    contextManager.addUserMessage("<system-reminder>\n" + preOut.additionalContext() + "\n</system-reminder>");
                }
            }

            // SessionTracker 记录工具调用
            if (ctx.sessionTracker != null) ctx.sessionTracker.onToolCall(tc.name);

            ToolDisplay.start(tc.name, effectiveArgs);
            ExecutionContext execCtx = new ExecutionContext(ctx.workingDir, ctx.autoApprove, System.out,
                ctx.config != null ? ctx.config.sandbox : null,
                ctx.activeSandboxRuntime != null ? ctx.activeSandboxRuntime : ctx.sandboxRuntime,
                ctx.lineReader, ctx.terminalWriter);
            long start = System.currentTimeMillis();
            ToolResult result = toolRegistry.execute(tc.name, effectiveArgs, execCtx);
            long durationMs = System.currentTimeMillis() - start;

            // PostToolUse hook
            String finalContent = result.content;
            if (ctx.hookManager != null && ctx.hookManager.hasHooks("PostToolUse")) {
                com.selfagent.hook.HookInput postInput = new com.selfagent.hook.HookInput(
                    "PostToolUse", ctx.sessionId, tc.name, effectiveArgs,
                    result.content, result.isError, null, null,
                    ctx.workingDir != null ? ctx.workingDir.toString() : "");
                com.selfagent.hook.HookOutput postOut = ctx.hookManager.fire("PostToolUse", postInput);
                if (postOut.updatedToolOutput() != null) finalContent = postOut.updatedToolOutput();
                if (postOut.additionalContext() != null) {
                    finalContent = finalContent + "\n\n[Hook context]: " + postOut.additionalContext();
                }
            }

            if (result.isError) {
                ToolDisplay.failure(tc.name, effectiveArgs, "failed");
            } else {
                ToolDisplay.success(tc.name, effectiveArgs, durationMs);
            }
            silentAppend(ctx, HistoryEvent.toolResult(ctx.sessionId, tc.id, tc.name,
                finalContent, result.isError, durationMs));
            contextManager.addToolResult(tc.id, finalContent);
        }
        // Selfcheck trigger：工具调用累计 ≥ 3 时提示验证
        if (ctx.selfcheckManager != null && ctx.selfcheckManager.incrementAndCheck()) {
            String msg = "\n\033[33m⚡ " + ctx.selfcheckManager.getCount() +
                " tool calls completed. Run \033[0m\033[36m/verify\033[0m\033[33m to check your work.\033[0m";
            if (ctx.terminalWriter != null) {
                ctx.terminalWriter.println(msg);
                ctx.terminalWriter.flush();
            } else {
                System.out.println(msg);
            }
        }
    }

    /**
     * 检查 backgroundAgentFutures 中已完成的任务，收集结果并移除，
     * 返回 task-notification 格式的 system-reminder 字符串，无完成任务则返回 null。
     */
    private String collectCompletedBackgroundAgents(AgentContext ctx) {
        if (ctx.backgroundAgentFutures.isEmpty()) return null;
        List<com.selfagent.agent.multi.SubAgentResult> done = new java.util.ArrayList<>();
        ctx.backgroundAgentFutures.removeIf(f -> {
            if (f.isDone()) {
                try { done.add(f.get()); } catch (Exception e) {
                    done.add(new com.selfagent.agent.multi.SubAgentResult(
                        "unknown", "unknown", "", "Agent failed: " + e.getMessage(), null, false, 0));
                }
                return true;
            }
            return false;
        });
        if (done.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Background agent(s) completed:\n\n");
        done.forEach(r -> sb.append(r.toTaskNotification()).append("\n\n"));
        return sb.toString().trim();
    }

    private void silentAppend(AgentContext ctx, HistoryEvent event) {
        ctx.historyStore.append(event);
    }

    private List<ObjectNode> buildToolDefs() {
        List<ObjectNode> defs = new ArrayList<>();
        for (ToolDefinition td : toolRegistry.getDefinitions()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", td.name);
            node.put("description", td.description);
            node.set("input_schema", td.inputSchema);
            defs.add(node);
        }
        if (currentSkillManager != null && !currentSkillManager.getLoaded().isEmpty()) {
            defs.add(buildSkillToolDef(currentSkillManager));
        }
        return defs;
    }

    private ObjectNode buildSkillToolDef(com.selfagent.skill.SkillManager skillManager) {
        StringBuilder desc = new StringBuilder(
            "Execute a skill to get specialized behavior. " +
            "Invoke this tool BEFORE responding when the user's request matches a skill. " +
            "After invoking, you MUST continue to fulfill the user's original request using the skill's guidance.\n\nAvailable skills:\n");
        skillManager.getLoaded().forEach(s ->
            desc.append("- ").append(s.name).append(": ").append(s.description).append("\n"));

        ObjectNode node = mapper.createObjectNode();
        node.put("name", "Skill");
        node.put("description", desc.toString().trim());
        ObjectNode schema = node.putObject("input_schema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("skill").put("type", "string").put("description", "The skill name to activate");
        props.putObject("args").put("type", "string").put("description", "The user's original request or relevant arguments to pass into the skill (e.g. PR number, file path, task description). Pass the full user input if the skill needs context to execute.");
        schema.putArray("required").add("skill");
        return node;
    }

    private String buildSkillReminder(com.selfagent.skill.SkillManager skillManager) {
        StringBuilder sb = new StringBuilder("The following skills are available for use with the Skill tool:\n\n");
        skillManager.getLoaded().forEach(s ->
            sb.append("- ").append(s.name).append(": ").append(s.description).append("\n"));
        return sb.toString().trim();
    }

    private record StreamResult(String content, String thinkingContent, List<ToolCall> toolCalls) {}
}
