/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public final class TasksExtension implements McpExtension {

    public static final String ID = "io.modelcontextprotocol/tasks";

    private static final JsonNode CREATE_TASK_SCHEMA = buildSchema();

    private static JsonNode buildSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        props.putObject("name").put("type", "string").put("description", "Task name");
        props.putObject("description").put("type", "string").put("description", "Task description");
        schema.putArray("required").add("name");
        return schema;
    }

    @Override
    public String extensionId() {
        return ID;
    }

    @Override
    public void bootstrap(McpServer server) {
        var descriptor = ToolDescriptor.builder("create_task")
                .description("Create a new task")
                .inputSchema(CREATE_TASK_SCHEMA)
                .extensionId(ID)
                .build();

        server.registerTool(new CreateTaskHandler(descriptor, server));

        server.resources()
                .addTemplate(new ResourceTemplateEntry(
                        "task-status", "task://{id}", "Current status of a task", "text/plain", id -> {
                            var entry = server.tasks().getById(id);
                            var text = entry != null ? entry.status().name() : "not_found";
                            return new TextResourceContents("task://" + id, "text/plain", text);
                        }));
    }

    private static final class CreateTaskHandler extends AbstractAsyncToolHandler {

        private final TaskRegistry tasks;
        private final Executor executor;

        CreateTaskHandler(ToolDescriptor descriptor, McpServer server) {
            super(descriptor);
            this.tasks = server.tasks();
            this.executor = server.executor();
        }

        @Override
        public CompletionStage<Object> handleAsync(McpContext context, Object arguments) {
            return CompletableFuture.supplyAsync(
                    () -> {
                        String name = "unnamed";
                        String description = null;
                        if (arguments instanceof Map<?, ?> map) {
                            if (map.get("name") instanceof String s) name = s;
                            if (map.get("description") instanceof String s) description = s;
                        }
                        return ToolResult.text(
                                tasks.createTask(name, description).id());
                    },
                    executor);
        }
    }
}
