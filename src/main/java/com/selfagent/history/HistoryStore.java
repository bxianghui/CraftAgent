package com.selfagent.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HistoryStore {
    private final Path baseDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "history-store");
        t.setDaemon(true);
        return t;
    });

    public HistoryStore(Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir);
    }

    public static HistoryStore defaultStore() throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), ".self-agent", "sessions");
        return new HistoryStore(dir);
    }

    /** 异步追加事件，不阻塞调用方 */
    public void append(HistoryEvent event) {
        writer.submit(() -> {
            try {
                Path file = sessionFile(event.sessionId);
                Files.createDirectories(file.getParent());
                ObjectNode node = mapper.createObjectNode();
                node.put("ts", event.ts);
                node.put("type", event.type.name());
                node.put("sessionId", event.sessionId);
                node.set("payload", mapper.valueToTree(event.payload));
                Files.writeString(file, mapper.writeValueAsString(node) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        });
    }

    /** session 结束时调用，等待所有待写事件完成后关闭（最多 5 秒） */
    public void flush() {
        try {
            writer.submit(() -> {}).get(5, TimeUnit.SECONDS);
            writer.shutdown();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<HistoryEvent> readSession(String sessionId) throws IOException {
        Path file = sessionFile(sessionId);
        if (!Files.exists(file)) return List.of();
        List<HistoryEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) continue;
            try {
                JsonNode node = mapper.readTree(line);
                HistoryEventType type = HistoryEventType.valueOf(node.get("type").asText());
                Map<String, Object> payload = mapper.convertValue(node.get("payload"), Map.class);
                events.add(new HistoryEvent(type, node.get("sessionId").asText(), payload));
            } catch (Exception ignored) {}
        }
        return events;
    }

    public List<String> listSessions() throws IOException {
        if (!Files.exists(baseDir)) return List.of();
        return Files.list(baseDir)
            .filter(Files::isDirectory)
            .map(p -> p.getFileName().toString())
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
    }

    private Path sessionFile(String sessionId) {
        return baseDir.resolve(sessionId).resolve("history.jsonl");
    }
}
