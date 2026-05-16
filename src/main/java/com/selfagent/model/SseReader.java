package com.selfagent.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class SseReader {
    /**
     * 逐行读取 SSE 流，对每个 data: 行调用 consumer（不含 "data: " 前缀）。
     * "[DONE]" 数据行自动跳过。
     */
    public static void read(InputStream stream, Consumer<String> consumer) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                if (!data.isEmpty()) consumer.accept(data);
            }
        }
    }
}
