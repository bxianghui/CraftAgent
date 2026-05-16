package com.selfagent.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RagStoreTest {
    @Test
    void storeAndSearchReturnsRelevantChunk(@TempDir Path tmp) throws IOException {
        RagStore store = new RagStore(tmp, 4);
        store.add("doc1", "test.md",
            List.of("chunk about cats", "chunk about dogs"),
            List.of(new float[]{1f, 0f, 0f, 0f}, new float[]{0f, 1f, 0f, 0f}));

        List<RagChunk> results = store.search(new float[]{1f, 0f, 0f, 0f}, 1);
        assertEquals(1, results.size());
        assertEquals("chunk about cats", results.get(0).text());
    }

    @Test
    void saveAndLoadPreservesChunks(@TempDir Path tmp) throws IOException {
        RagStore store = new RagStore(tmp, 4);
        store.add("doc1", "test.md",
            List.of("hello world"),
            List.of(new float[]{1f, 0f, 0f, 0f}));
        store.save();

        RagStore loaded = RagStore.load(tmp, 4);
        List<RagChunk> results = loaded.search(new float[]{1f, 0f, 0f, 0f}, 1);
        assertEquals("hello world", results.get(0).text());
    }

    @Test
    void deleteRemovesChunkFromSearchResults(@TempDir Path tmp) throws IOException {
        RagStore store = new RagStore(tmp, 4);
        store.add("doc1", "a.md",
            List.of("chunk about cats"),
            List.of(new float[]{1f, 0f, 0f, 0f}));
        store.add("doc2", "b.md",
            List.of("chunk about dogs"),
            List.of(new float[]{0f, 1f, 0f, 0f}));

        store.delete("doc1");

        List<RagChunk> results = store.search(new float[]{1f, 0f, 0f, 0f}, 5);
        assertTrue(results.stream().noneMatch(c -> c.docId().equals("doc1")));
        assertEquals(1, store.size());
    }

    @Test
    void enabledDefaultIsTrue(@TempDir Path tmp) {
        RagStore store = new RagStore(tmp, 4);
        assertTrue(store.isEnabled());
    }

    @Test
    void toggleEnabledPersists(@TempDir Path tmp) throws IOException {
        RagStore store = new RagStore(tmp, 4);
        store.setEnabled(false);
        store.saveConfig();

        RagStore loaded = RagStore.load(tmp, 4);
        assertFalse(loaded.isEnabled());
    }
}
