package com.selfagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

public interface McpTransport {
    void connect() throws IOException;

    /**
     * 发送消息并返回响应。
     * 同步实现（HTTP）直接返回响应 JsonNode；
     * 异步实现（stdio）发送后返回 null，调用方需再调用 receive()。
     */
    JsonNode send(String message) throws IOException;

    /**
     * 仅异步实现需要实现此方法，读取下一条响应。
     * 同步实现默认返回 null（不需要调用）。
     */
    default JsonNode receive() throws IOException { return null; }

    boolean isAsync();

    void close();
}
