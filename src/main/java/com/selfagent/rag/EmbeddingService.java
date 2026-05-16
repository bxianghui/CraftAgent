package com.selfagent.rag;

public interface EmbeddingService {
    float[] embed(String text);
    int dimensions();
}
