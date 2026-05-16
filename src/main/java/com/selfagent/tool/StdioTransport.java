package com.selfagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

public class StdioTransport implements McpTransport {
    private final String[] command;
    private final ObjectMapper mapper = new ObjectMapper();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public StdioTransport(String... command) {
        this.command = command;
    }

    public StdioTransport(Process process) {
        this.command = null;
        this.process = process;
    }

    @Override
    public void connect() throws IOException {
        if (process == null) {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            process = pb.start();
        }
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    @Override
    public JsonNode send(String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
        return null; // async: caller must call receive()
    }

    @Override
    public JsonNode receive() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("MCP server closed connection");
        return mapper.readTree(line);
    }

    @Override
    public boolean isAsync() { return true; }

    @Override
    public void close() {
        try { if (writer != null) writer.close(); } catch (IOException ignored) {}
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        if (process != null) process.destroyForcibly();
    }
}
