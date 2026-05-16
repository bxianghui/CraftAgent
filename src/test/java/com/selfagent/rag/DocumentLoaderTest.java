package com.selfagent.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DocumentLoaderTest {
    @Test
    void loadsTxtFile(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("doc.txt");
        Files.writeString(f, "hello world");
        DocumentLoader loader = new DocumentLoader();
        assertEquals("hello world", loader.loadFile(f));
    }

    @Test
    void loadsMdFile(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("doc.md");
        Files.writeString(f, "# Title\ncontent");
        DocumentLoader loader = new DocumentLoader();
        assertEquals("# Title\ncontent", loader.loadFile(f));
    }

    @Test
    void throwsOnMissingFile(@TempDir Path tmp) {
        DocumentLoader loader = new DocumentLoader();
        assertThrows(IOException.class, () -> loader.loadFile(tmp.resolve("missing.txt")));
    }
}
