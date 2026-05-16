package com.selfagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class SessionStore {
    private final Path baseDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public SessionStore(Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir);
    }

    public void append(String sessionId, String type, String content) throws IOException {
        Path file = sessionFile(sessionId);
        Files.createDirectories(file.getParent());
        ObjectNode entry = mapper.createObjectNode();
        entry.put("ts", Instant.now().toString());
        entry.put("type", type);
        entry.put("content", content);
        Files.writeString(file, mapper.writeValueAsString(entry) + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public List<String> readSession(String sessionId) throws IOException {
        Path file = sessionFile(sessionId);
        if (!Files.exists(file)) return List.of();
        return Files.readAllLines(file);
    }

    public List<String> listSessions() throws IOException {
        if (!Files.exists(baseDir)) return List.of();
        return Files.list(baseDir)
            .filter(Files::isDirectory)
            .map(p -> p.getFileName().toString())
            .sorted()
            .collect(Collectors.toList());
    }

    private Path sessionFile(String sessionId) {
        return baseDir.resolve(sessionId).resolve("session.jsonl");
    }
}
