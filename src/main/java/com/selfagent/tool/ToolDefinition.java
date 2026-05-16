package com.selfagent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class ToolDefinition {
    public final String name;
    public final String description;
    public final ObjectNode inputSchema;

    public ToolDefinition(String name, String description, ObjectNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
}
