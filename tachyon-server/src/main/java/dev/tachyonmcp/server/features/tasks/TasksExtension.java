/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class TasksExtension implements ServerExtension {

    public static final String ID = "io.modelcontextprotocol/tasks";

    private static final JsonNode CREATE_TASK_SCHEMA = buildSchema();

    private static final TasksExtension INSTANCE = new TasksExtension();

    public static TasksExtension instance() {
        return INSTANCE;
    }

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
    public void bootstrap(Server server) {
        var descriptor = ToolDescriptor.builder("create_task")
                .description("Create a new task")
                .inputSchema(CREATE_TASK_SCHEMA)
                .extensionId(ID)
                .build();

        server.registerTool(new CreateTaskHandler(descriptor, server));

        server.resources()
                .addTemplate(ResourceTemplateEntry.of(
                        "task-status", "task://{id}", "Current status of a task", "text/plain", (ctx, uri, params) -> {
                            var id = params.get("id");
                            var entry = server.tasks().getById(id);
                            var text = entry != null ? entry.status().name() : "not_found";
                            return TextResourceContents.of(uri, "text/plain", text, null);
                        }));
    }

    private static final class CreateTaskHandler extends AbstractAsyncToolHandler {

        private final TaskRegistry tasks;
        private final Executor executor;

        CreateTaskHandler(ToolDescriptor descriptor, Server server) {
            super(descriptor);
            this.tasks = server.tasks();
            this.executor = server.executor();
        }

        @Override
        public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolArgs args) {
            var sessionId = OutboundSseStreamMessageRouter.currentSessionId();
            var outboundStream = OutboundSseStreamMessageRouter.currentOutboundSseStream();
            return CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return OutboundSseStreamMessageRouter.withDispatchContext(sessionId, outboundStream, () -> {
                                var name = args.stringOr("name", "unnamed");
                                var description = args.stringOpt("description").orElse(null);
                                return ToolResult.text(
                                        tasks.createTask(name, description).id());
                            });
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    },
                    executor);
        }
    }
}
