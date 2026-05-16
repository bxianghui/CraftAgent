package com.selfagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jelmerk.knn.DistanceFunctions;
import com.github.jelmerk.knn.Item;
import com.github.jelmerk.knn.SearchResult;
import com.github.jelmerk.knn.hnsw.HnswIndex;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class RagStore {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Path storeDir;
    private final int dimensions;
    private HnswIndex<String, float[], RagItem, Float> index;
    private final Map<String, RagChunk> chunkMap = new LinkedHashMap<>();
    private boolean enabled;

    public RagStore(Path storeDir, int dimensions) {
        this.storeDir = storeDir;
        this.dimensions = dimensions;
        this.enabled = true;
        this.index = buildIndex();
    }

    private HnswIndex<String, float[], RagItem, Float> buildIndex() {
        return HnswIndex.<float[], Float>newBuilder(dimensions, DistanceFunctions.FLOAT_COSINE_DISTANCE, 10_000)
            .withM(16).withEfConstruction(200).withEf(50).build();
    }

    public void add(String docId, String source, List<String> chunks, List<float[]> vectors) {
        for (int i = 0; i < chunks.size(); i++) {
            String id = docId + "#" + i;
            RagChunk chunk = new RagChunk(docId, source, i, chunks.get(i), 0f);
            chunkMap.put(id, chunk);
            try {
                index.add(new RagItem(id, vectors.get(i)));
            } catch (Exception e) {
                System.err.println("[RAG] Failed to add chunk: " + e.getMessage());
            }
        }
    }

    public List<RagChunk> search(float[] queryVector, int topK) {
        List<SearchResult<RagItem, Float>> results = index.findNearest(queryVector, topK);
        List<RagChunk> chunks = new ArrayList<>();
        for (SearchResult<RagItem, Float> r : results) {
            RagChunk c = chunkMap.get(r.item().id());
            if (c != null) {
                chunks.add(new RagChunk(c.docId(), c.source(), c.chunkIndex(), c.text(), r.distance()));
            }
        }
        return chunks;
    }

    public void delete(String docId) {
        chunkMap.entrySet().removeIf(e -> e.getValue().docId().equals(docId));
        // HNSW 不支持单条删除，重建索引过滤掉已删除的向量
        rebuildIndex();
    }

    private void rebuildIndex() {
        HnswIndex<String, float[], RagItem, Float> newIndex = buildIndex();
        for (RagItem item : index.items()) {
            if (chunkMap.containsKey(item.id())) {
                try { newIndex.add(item); } catch (Exception ignored) {}
            }
        }
        this.index = newIndex;
    }

    public void save() throws IOException {
        Files.createDirectories(storeDir);
        mapper.writeValue(storeDir.resolve("chunks.json").toFile(), chunkMap);
        try (OutputStream os = Files.newOutputStream(storeDir.resolve("index.bin"))) {
            index.save(os);
        }
        saveConfig();
    }

    public void saveConfig() throws IOException {
        Files.createDirectories(storeDir);
        ObjectNode cfg = mapper.createObjectNode();
        cfg.put("enabled", enabled);
        cfg.put("dimensions", dimensions);
        mapper.writeValue(storeDir.resolve("config.json").toFile(), cfg);
    }

    @SuppressWarnings("unchecked")
    public static RagStore load(Path storeDir, int dimensions) throws IOException {
        RagStore store = new RagStore(storeDir, dimensions);
        Path cfgPath = storeDir.resolve("config.json");
        if (Files.exists(cfgPath)) {
            ObjectNode cfg = (ObjectNode) mapper.readTree(cfgPath.toFile());
            store.enabled = cfg.path("enabled").asBoolean(true);
        }
        Path chunksPath = storeDir.resolve("chunks.json");
        if (Files.exists(chunksPath)) {
            Map<String, RagChunk> loaded = mapper.readValue(chunksPath.toFile(),
                mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, RagChunk.class));
            store.chunkMap.putAll(loaded);
        }
        Path indexPath = storeDir.resolve("index.bin");
        if (Files.exists(indexPath)) {
            store.index = HnswIndex.load(indexPath);
        }
        return store;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEmpty() { return chunkMap.isEmpty(); }
    public int size() { return chunkMap.size(); }
    public List<String> listDocIds() {
        return chunkMap.values().stream().map(RagChunk::docId).distinct().toList();
    }

    public record RagItem(String id, float[] vector) implements Item<String, float[]> {
        @Override public String id() { return id; }
        @Override public float[] vector() { return vector; }
        @Override public int dimensions() { return vector.length; }
    }
}
