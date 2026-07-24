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
import dev.tachyonmcp.server.json.JsonSchema;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class TasksExtension implements ServerExtension {

    public static final String ID = "io.modelcontextprotocol/tasks";

    // language=json
    private static final JsonSchema CREATE_TASK_SCHEMA = JsonSchema.of("""
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "Task name"
            },
            "description": {
              "type": "string",
              "description": "Task description"
            }
          },
          "required": ["name"]
        }
        """);

    private static final TasksExtension INSTANCE = new TasksExtension();

    public static TasksExtension instance() {
        return INSTANCE;
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
                .registerTemplate(ResourceTemplateDescriptor.of("task-status", "task://{id}"), (ctx, request) -> {
                    var id = request.params().get("id").scalarValue();
                    var entry = server.tasks().get(id);
                    var text = entry != null ? entry.status().name() : "not_found";
                    return TextResourceContents.of(request.uri(), text, "text/plain", null);
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
                                final var meta = new HashMap<String, Object>(2);
                                args.stringOpt("name").ifPresent(name -> meta.put("name", name));
                                args.stringOpt("description")
                                        .ifPresent(description -> meta.put("description", description));
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
