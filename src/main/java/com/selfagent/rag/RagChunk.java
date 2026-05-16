package com.selfagent.rag;

public record RagChunk(String docId, String source, int chunkIndex, String text, float score) {}
