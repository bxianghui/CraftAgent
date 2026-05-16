package com.selfagent.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChunkSplitterTest {
    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    void splitsMdByHeadings() {
        String md = "# Title\n\n## Section 1\ncontent1\n\n## Section 2\ncontent2";
        List<String> chunks = splitter.split(md, "md");
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("Section 1"));
        assertTrue(chunks.get(1).contains("Section 2"));
    }

    @Test
    void splitsTextByParagraphs() {
        String text = "para1 line1\npara1 line2\n\npara2 content\n\npara3 content";
        List<String> chunks = splitter.split(text, "text");
        assertEquals(3, chunks.size());
    }

    @Test
    void fallsBackToFixedSizeForLongChunks() {
        String longText = "a".repeat(600);
        List<String> chunks = splitter.split(longText, "text");
        assertTrue(chunks.size() >= 2);
        chunks.forEach(c -> assertTrue(c.length() <= 512 + 50));
    }

    @Test
    void doesNotReturnEmptyChunks() {
        String md = "## Section\n\n\n\n## Empty\n\n## Real\ncontent";
        List<String> chunks = splitter.split(md, "md");
        chunks.forEach(c -> assertFalse(c.isBlank()));
    }
}
