/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.extensions.ExtensionContext;
import dev.tachyonmcp.api.server.extensions.ServerExtension;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskOptions;
import dev.tachyonmcp.api.server.features.tasks.Tasks;
import dev.tachyonmcp.api.server.features.tools.AsyncToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

public class TasksExtension implements ServerExtension {

    public static final String ID = "io.modelcontextprotocol/tasks";

    /**
     * 2026-07-28 requires every task-augmented request (task-augmented {@code tools/call},
     * {@code tasks/get}, {@code tasks/cancel}) to declare this extension per request (SEP-2663) or be
     * rejected with {@code -32021}; 2025-11-25's legacy, session-negotiated task support predates the
     * extension and isn't gated by it.
     */
    public static @Nullable ServerError requireDeclared(DispatchContext context) {
        if (context.protocol().supportsSessions() || context.isExtensionEnabled(ID)) {
            return null;
        }
        return ServerErrors.missingRequiredClientCapability(
                "Requires the '" + ID + "' extension", Map.of("extensions", Map.of(ID, Map.of())));
    }

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
    public void bootstrap(ExtensionContext server) {
        var descriptor = ToolDescriptor.builder()
                .name("create_task")
                .description("Create a new task")
                .inputSchema(CREATE_TASK_SCHEMA)
                .extensionId(ID)
                .build();

        server.tools().registerAsync(descriptor, new CreateTaskFn(server));

        var taskStatusTemplate = ResourceTemplateDescriptor.builder()
                .name("task-status")
                .uriTemplate("task://{id}")
                .build();
        server.resources().registerTemplate(taskStatusTemplate, (ctx, request) -> {
            var id = request.params().get("id").scalarValue();
            var entry = server.tasks().get(id);
            var text = entry != null ? entry.status().name() : "not_found";
            return TextResourceContents.of(request.uri(), text, "text/plain", null);
        });
    }

    private static final class CreateTaskFn implements AsyncToolFn {

        private final Tasks tasks;
        private final Executor executor;

        CreateTaskFn(ExtensionContext server) {
            this.tasks = server.tasks();
            this.executor = server.executor();
        }

        @Override
        public CompletionStage<? extends ToolResult> apply(InteractionContext context, ToolRequest request) {
            var args = request.arguments();
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
