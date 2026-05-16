package com.selfagent.history;

import com.selfagent.model.ChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Session 级别的请求日志，记录每次发给模型的完整 input。
 * 存储路径：~/.self-agent/sessions/<sessionId>/requests.json（JSON 数组）
 *
 * 每次追加后实时写文件，确保异常退出时已有记录不丢失。
 * flush() 等待所有异步任务完成后关闭。
 */
public class SessionLogger {
    private final Path baseDir;
    private final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "session-logger");
        t.setDaemon(true);
        return t;
    });

    // 只在 writer 线程内访问，无需同步
    private List<Map<String, Object>> prevMessages = List.of();
    private String prevTs = null;
    private Path currentFile = null;
    private final List<ObjectNode> entries = new ArrayList<>();

    public SessionLogger(Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir);
    }

    public static SessionLogger defaultLogger() throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), ".self-agent", "sessions");
        return new SessionLogger(dir);
    }

    public void logRequest(String sessionId, ChatRequest request) {
        List<Map<String, Object>> messages = deepCopy(request.messages);
        String model = request.model;
        String systemPrompt = request.systemPrompt;
        List<com.fasterxml.jackson.databind.node.ObjectNode> tools = request.tools;

        writer.submit(() -> {
            try {
                if (currentFile == null) {
                    Path dir = baseDir.resolve(sessionId);
                    Files.createDirectories(dir);
                    currentFile = dir.resolve("requests.json");
                }

                String ts = Instant.now().toString();
                ObjectNode entry = mapper.createObjectNode();
                entry.put("ts", ts);
                entry.put("sessionId", sessionId);
                if (model != null) entry.put("model", model);
                if (systemPrompt != null) entry.put("system", systemPrompt);

                int commonPrefix = commonPrefixLength(prevMessages, messages);
                if (commonPrefix > 0 && prevTs != null) {
                    entry.put("prev_ref", prevTs);
                    entry.put("prev_ref_count", commonPrefix);
                    ArrayNode delta = entry.putArray("delta_messages");
                    for (int i = commonPrefix; i < messages.size(); i++) {
                        delta.add(mapper.valueToTree(messages.get(i)));
                    }
                } else {
                    entry.set("messages", mapper.valueToTree(messages));
                }

                if (tools != null && !tools.isEmpty()) {
                    entry.set("tools", mapper.valueToTree(tools));
                }

                entries.add(entry);
                prevMessages = messages;
                prevTs = ts;

                // 每次追加后实时写文件，异常退出时已有记录不丢失
                writeFile();
            } catch (Exception ignored) {}
        });
    }

    private void writeFile() {
        if (currentFile == null || entries.isEmpty()) return;
        try {
            ArrayNode array = mapper.createArrayNode();
            entries.forEach(array::add);
            Files.writeString(currentFile, mapper.writeValueAsString(array));
        } catch (Exception ignored) {}
    }

    /** session 结束时调用，等待所有待写任务完成后关闭（最多 5 秒） */
    public void flush() {
        try {
            writer.submit(() -> {}).get(5, TimeUnit.SECONDS);
            writer.shutdown();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deepCopy(List<Map<String, Object>> messages) {
        try {
            String json = mapper.writeValueAsString(messages);
            return mapper.readValue(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>(messages);
        }
    }

    private int commonPrefixLength(List<Map<String, Object>> prev, List<Map<String, Object>> curr) {
        int len = Math.min(prev.size(), curr.size());
        for (int i = 0; i < len; i++) {
            try {
                JsonNode a = mapper.valueToTree(prev.get(i));
                JsonNode b = mapper.valueToTree(curr.get(i));
                if (!a.equals(b)) return i;
            } catch (Exception e) {
                return i;
            }
        }
        return len;
    }
}
