/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Convenient base for synchronous (blocking) tool handlers.
 *
 * <p>See {@link ToolHandler} for the virtual-thread contract — {@link #handle} runs on a VT,
 * never use {@code synchronized}, use {@link java.util.concurrent.locks.ReentrantLock} instead.
 */
public interface SyncToolHandler extends ToolHandler {

    /**
     * The tool name.
     */
    String name();

    @Nullable
    default String title() {
        return null;
    }

    @Nullable
    default String description() {
        return null;
    }

    @Nullable
    default JsonNode inputSchema() {
        return null;
    }

    @Nullable
    default JsonNode outputSchema() {
        return null;
    }

    @Nullable
    default TaskSupport taskSupport() {
        return null;
    }

    /**
     * Executes the tool synchronously.
     */
    ToolResult handle(InteractionContext context, ToolArgs args) throws Exception;

    @Nullable
    default ToolAnnotations annotations() {
        return null;
    }

    @Override
    default ToolDescriptor descriptor() {
        return ToolDescriptor.builder()
                .name(name())
                .title(title())
                .description(description())
                .inputSchema(inputSchema())
                .outputSchema(outputSchema())
                .taskSupport(taskSupport())
                .annotations(annotations())
                .build();
    }

    @Override
    default ToolResult handle(InteractionContext context, ToolRequest request) throws Exception {
        return handle(context, ToolArgs.of(request.arguments(), request.payloadDeserializer()));
    }

    /**
     * Creates a simple SyncToolHandler from a name, description, schema, and function.
     */
    static SyncToolHandler of(
            String name,
            @Nullable String description,
            @Nullable JsonNode inputSchema,
            BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return new SyncToolHandler() {

            @Override
            public String name() {
                return name;
            }

            @Override
            @Nullable
            public String description() {
                return description;
            }

            @Override
            @Nullable
            public JsonNode inputSchema() {
                return inputSchema;
            }

            @Override
            public ToolResult handle(InteractionContext ctx, ToolArgs args) {
                return fn.apply(ctx, args);
            }
        };
    }

    /**
     * Creates a simple SyncToolHandler with JSON string input and output schemas.
     */
    static SyncToolHandler of(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        var desc = ToolDescriptor.builder()
                .name(name)
                .inputSchema(parseSchema(inputSchemaJson, name))
                .outputSchema(parseSchema(outputSchemaJson, name))
                .description(description)
                .build();
        return fromDescriptor(desc, fn);
    }

    /**
     * Creates a simple SyncToolHandler with {@link JsonNode} input and output schemas.
     */
    static SyncToolHandler of(
            String name,
            @Nullable String description,
            @Nullable JsonNode inputSchema,
            @Nullable JsonNode outputSchema,
            BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        var desc = ToolDescriptor.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .build();
        return fromDescriptor(desc, fn);
    }

    /**
     * Creates a SyncToolHandler from a pre-built descriptor and function.
     */
    static SyncToolHandler fromDescriptor(
            ToolDescriptor desc, BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return new SyncToolHandler() {
            @Override
            public String name() {
                return desc.name();
            }

            @Override
            @Nullable
            public String title() {
                return desc.title();
            }

            @Override
            @Nullable
            public String description() {
                return desc.description();
            }

            @Override
            @Nullable
            public JsonNode inputSchema() {
                return desc.inputSchema();
            }

            @Override
            @Nullable
            public JsonNode outputSchema() {
                return desc.outputSchema();
            }

            @Override
            public ToolResult handle(InteractionContext ctx, ToolArgs args) throws Exception {
                return fn.apply(ctx, args);
            }
        };
    }
}
