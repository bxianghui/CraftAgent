package com.selfagent.cli;

import com.selfagent.agent.AgentContext;
import com.selfagent.agent.ContextManager;
import com.selfagent.agent.ReactLoop;
import com.selfagent.agent.multi.SubAgentResult;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.widget.AutosuggestionWidgets;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ReplLoop {
    private static final List<String> SLASH_COMMANDS = List.of(
        "/help", "/clear", "/tools",
        "/history", "/history last",
        "/mcp", "/mcp refresh", "/mcp enable", "/mcp disable",
        "/memory", "/memory long", "/remember", "/forget",
        "/model", "/models", "/sessions",
        "/skills", "/skill", "/skill refresh", "/import",
        "/rag", "/rag on", "/rag off",
        "/timing", "/timing on", "/timing off",
        "/thinking", "/thinking on", "/thinking off",
        "/sandbox", "/sandbox on", "/sandbox off",
        "/agent", "/agent explore", "/agent coder", "/agent reviewer",
        "/verify"
    );

    private final ReactLoop reactLoop;
    private final ContextManager contextManager;
    private final SlashCommandRouter slashRouter;

    public ReplLoop(ReactLoop reactLoop, ContextManager contextManager,
                    SlashCommandRouter slashRouter) {
        this.reactLoop = reactLoop;
        this.contextManager = contextManager;
        this.slashRouter = slashRouter;
    }

    public void start(AgentContext ctx) throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .parser(new DefaultParser())
            .completer(new SlashCompleter())
            .variable(LineReader.HISTORY_FILE,
                Paths.get(System.getProperty("user.home"), ".self-agent", "input_history").toString())
            .variable(LineReader.HISTORY_SIZE, 100)
            .variable(LineReader.SUGGESTIONS_MIN_BUFFER_SIZE, 1)
            .build();

        AutosuggestionWidgets autosuggestion = new AutosuggestionWidgets(reader);
        autosuggestion.enable();
        reader.setAutosuggestion(LineReader.SuggestionType.HISTORY);

        if (ctx != null) {
            ctx.lineReader = reader;
            ctx.terminalWriter = terminal.writer();
        }

        // 后台 agent 监听线程：完成时自动打印结果
        if (ctx != null) {
            final Terminal t = terminal;
            final LineReader r = reader;
            Thread bgWatcher = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try { Thread.sleep(300); } catch (InterruptedException e) { break; }
                    if (ctx.backgroundAgentFutures.isEmpty()) continue;
                    List<com.selfagent.agent.multi.SubAgentResult> done = new java.util.ArrayList<>();
                    ctx.backgroundAgentFutures.removeIf(f -> {
                        if (f.isDone() && !f.isCancelled()) {
                            try { done.add(f.get()); } catch (Exception e) {
                                done.add(new com.selfagent.agent.multi.SubAgentResult(
                                    "unknown", "unknown", "", "Agent failed: " + e.getMessage(), null, false, 0));
                            }
                            return true;
                        }
                        return false;
                    });
                    if (done.isEmpty()) continue;
                    done.forEach(res -> {
                        r.printAbove("\033[32m✓ [bg] Agent(" + res.subagentType() + ")  "
                            + res.description() + "  (" + res.durationMs() + "ms)\033[0m");
                        if (res.result() != null && !res.result().isBlank()) {
                            for (String line : res.result().split("\n")) {
                                r.printAbove(line);
                            }
                        }
                    });
                    if (ctx.contextManager != null) {
                        StringBuilder sb = new StringBuilder("Background agent(s) completed:\n\n");
                        done.forEach(res -> sb.append(res.toTaskNotification()).append("\n\n"));
                        ctx.contextManager.addUserMessage(
                            "<system-reminder>\n" + sb.toString().trim() + "\n</system-reminder>");
                    }
                }
            }, "bg-watcher");
            bgWatcher.setDaemon(true);
            bgWatcher.start();
        }

        terminal.writer().println("\033[32mself-agent\033[0m — /help for commands, Ctrl+C to exit");

        while (true) {
            String line;
            try {
                line = reader.readLine("\033[36m❯\033[0m ");
            } catch (UserInterruptException e) {
                break;
            } catch (EndOfFileException e) {
                break;
            }
            if (line == null || line.isBlank()) continue;

            if (SlashCommandRouter.isSlashCommand(line)) {
                slashRouter.handle(line, ctx);
                continue;
            }

            handleUserInput(line, ctx, terminal, reader);
        }

        // 退出前等待未完成的后台 agent（最多 30 秒），避免结果丢失
        if (ctx != null && !ctx.backgroundAgentFutures.isEmpty()) {
            terminal.writer().println("\033[33mWaiting for background agents to finish...\033[0m");
            terminal.writer().flush();
            for (var f : ctx.backgroundAgentFutures) {
                try { f.get(30, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        }
        terminal.writer().println("Bye!");
    }

    private void handleUserInput(String line, AgentContext ctx, Terminal terminal, LineReader reader) {
        // UserPromptSubmit hook
        if (ctx != null && ctx.hookManager != null && ctx.hookManager.hasHooks("UserPromptSubmit")) {
            com.selfagent.hook.HookInput hi = new com.selfagent.hook.HookInput(
                "UserPromptSubmit", ctx.sessionId, null, null, null, false,
                line, null, ctx.workingDir != null ? ctx.workingDir.toString() : "");
            com.selfagent.hook.HookOutput ho = ctx.hookManager.fire("UserPromptSubmit", hi);
            if (!ho.continueExecution()) {
                terminal.writer().println("\033[33m[Hook] " + (ho.stopReason() != null
                    ? ho.stopReason() : "Blocked") + "\033[0m");
                return;
            }
            if (ho.additionalContext() != null && ctx.contextManager != null)
                ctx.contextManager.setSystemPromptSuffix(ho.additionalContext());
        }

        SpinnerThread spinner = new SpinnerThread(terminal);
        spinner.start();

        try {
            reactLoop.streamRun(line, ctx,
                chunk -> {
                    spinner.stop();
                    terminal.writer().print(chunk);
                    terminal.writer().flush();
                },
                spinner::restart,   // 工具调用后等下一轮 LLM 时重启动画
                spinner::stop       // 下一轮 LLM 第一个 chunk 到来时停止
            );
            long elapsed = spinner.elapsedMs();
            spinner.stop();
            terminal.writer().println();
            terminal.writer().println("\033[32m* Churned for " + SpinnerThread.formatElapsed(elapsed / 1000) + "\033[0m");
            terminal.writer().flush();
        } catch (Exception e) {
            spinner.stop();
            terminal.writer().println("\033[31mError: " + e.getMessage() + "\033[0m");
        }

        // 检查已完成的后台 agent
        if (ctx != null && !ctx.backgroundAgentFutures.isEmpty()) {
            collectAndPrintDone(ctx, terminal);
        }
    }

    private void collectAndPrintDone(AgentContext ctx, Terminal terminal) {
        List<SubAgentResult> done = new ArrayList<>();
        ctx.backgroundAgentFutures.removeIf(f -> {
            if (f.isDone() && !f.isCancelled()) {
                try { done.add(f.get()); } catch (Exception e) {
                    done.add(new SubAgentResult("unknown", "unknown", "",
                        "Agent failed: " + e.getMessage(), null, false, 0));
                }
                return true;
            }
            return false;
        });
        done.forEach(r -> terminal.writer().println(
            "\033[32m✓ [bg] Agent(" + r.subagentType() + ")  "
            + r.description() + "  (" + r.durationMs() + "ms)\033[0m"));
        if (!done.isEmpty()) terminal.writer().flush();
    }

    private static class SpinnerThread {
        private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
        private static final String[] MESSAGES = {"thinking", "reasoning", "processing", "working"};
        private final Terminal terminal;
        private volatile Thread thread;
        private volatile boolean stopped = false;
        private final long startMs = System.currentTimeMillis();
        private volatile boolean onceStop = false; // stop 只清行一次

        SpinnerThread(Terminal terminal) {
            this.terminal = terminal;
            this.thread = buildThread();
        }

        private Thread buildThread() {
            String msg = MESSAGES[(int)(System.currentTimeMillis() % MESSAGES.length)];
            Thread t = new Thread(() -> {
                int i = 0;
                while (!stopped) {
                    long elapsed = (System.currentTimeMillis() - startMs) / 1000;
                    String frame = "\r\033[36m" + FRAMES[i % FRAMES.length] + "\033[0m \033[96m"
                        + msg + "...\033[0m \033[33m" + formatElapsed(elapsed) + "\033[0m";
                    terminal.writer().print(frame);
                    terminal.writer().flush();
                    i++;
                    try { Thread.sleep(80); } catch (InterruptedException e) { break; }
                }
                terminal.writer().print("\r\033[2K");
                terminal.writer().flush();
            }, "spinner");
            t.setDaemon(true);
            return t;
        }

        static String formatElapsed(long secs) {
            long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
            if (h > 0) return h + "h" + m + "m" + s + "s";
            if (m > 0) return m + "m" + s + "s";
            return s + "s";
        }

        long elapsedMs() { return System.currentTimeMillis() - startMs; }

        void start() { stopped = false; thread.start(); }

        synchronized void stop() {
            if (stopped) return;
            stopped = true;
            thread.interrupt();
            try { thread.join(200); } catch (InterruptedException ignored) {}
        }

        /** 工具调用后重启动画，等待下一轮 LLM 响应 */
        synchronized void restart() {
            stop();
            stopped = false;
            thread = buildThread();
            thread.start();
        }
    }

    private static class SlashCompleter implements Completer {
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            if (!word.startsWith("/")) return;
            SLASH_COMMANDS.stream()
                .filter(cmd -> cmd.startsWith(word))
                .forEach(cmd -> candidates.add(new Candidate(cmd)));
        }
    }
}
