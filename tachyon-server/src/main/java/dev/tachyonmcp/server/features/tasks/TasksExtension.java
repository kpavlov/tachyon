/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.internal.ServerEngine;
import java.util.HashMap;
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

    /**
     * Registers the task creation tool and task status resource template with the server.
     */
    @Override
    public void bootstrap(ServerEngine server) {
        var descriptor = ToolDescriptor.builder()
                .name("create_task")
                .description("Create a new task")
                .inputSchema(CREATE_TASK_SCHEMA)
                .extensionId(ID)
                .build();

        server.tools().register(new CreateTaskHandler(descriptor, server));

        server.resources()
                .registerTemplate(
                        ResourceTemplateDescriptor.of("task-status", "task://{id}"),
                        (ctx, uri, params, uriTemplate) -> {
                            var id = params.get("id").scalarValue();
                            var entry = server.tasks().get(id);
                            var text = entry != null ? entry.status().name() : "not_found";
                            return TextResourceContents.of(uri, text, "text/plain", null);
                        });
    }

    private static final class CreateTaskHandler extends AbstractToolHandler {

        private final Tasks tasks;
        private final Executor executor;

        CreateTaskHandler(ToolDescriptor descriptor, ServerEngine server) {
            super(descriptor);
            this.tasks = server.tasks();
            this.executor = server.executor();
        }

        @Override
        public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, Args args) {
            var sessionId = OutboundSseStreamMessageRouter.currentSessionId();
            var outboundStream = OutboundSseStreamMessageRouter.currentOutboundSseStream();
            return CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return OutboundSseStreamMessageRouter.withDispatchContext(sessionId, outboundStream, () -> {
                                final var meta = new HashMap<String, JsonNode>(2);
                                final var name = args.nodeOr("name", null);
                                if (name != null) {
                                    meta.put("name", name);
                                }
                                final var description = args.nodeOr("description", null);
                                if (description != null) {
                                    meta.put("description", description);
                                }
                                final var task = tasks.create(TaskOptions.builder()
                                        .meta(!meta.isEmpty() ? meta : null)
                                        .build());
                                return ToolResult.text(task.id());
                            });
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    },
                    executor);
        }
    }
}
