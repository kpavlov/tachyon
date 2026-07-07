/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Demonstrates the two tool-handler patterns:
 * 1. SyncToolHandler.of() — inline lambda
 * 2. AbstractSyncToolHandler — reusable class
 */
final class ToolHandlerExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode GREET_SCHEMA = MAPPER.createObjectNode()
            .put("type", "object")
            .set(
                    "properties",
                    MAPPER.createObjectNode()
                            .set(
                                    "name",
                                    MAPPER.createObjectNode()
                                            .put("type", "string")
                                            .put("description", "Name to greet")))
            .set("required", MAPPER.createArrayNode().add("name"));

    /**
     * Lambda-style: use SyncToolHandler.of() inside a builder chain.
     */
    static SyncToolHandler lambdaHello() {
        return SyncToolHandler.of("hello", "Say hello", null, (ctx, args) -> ToolResult.text("Hello, world!"));
    }

    /**
     * Lambda with input schema and arguments.
     */
    static SyncToolHandler lambdaGreet() {
        return SyncToolHandler.of(
                "greeting",
                "Generates a personalized greeting",
                GREET_SCHEMA,
                (@NonNull InteractionContext ctx, ToolArgs args) -> {
                    String name = args.string("name");
                    return ToolResult.text("Hello, " + name + "!");
                });
    }

    /**
     * Class-based: extend AbstractSyncToolHandler.
     */
    static final class GreetingTool extends AbstractSyncToolHandler {
        GreetingTool() {
            super(ToolDescriptor.builder()
                    .name("greeting")
                    .title("Greeting")
                    .description("Generates a personalized greeting")
                    .inputSchema(GREET_SCHEMA)
                    .build());
        }

        @Override
        @NonNull
        public ToolResult handle(InteractionContext ctx, ToolArgs args) {
            var name = args.string("name");
            return ToolResult.text("Hello, " + name + "!");
        }
    }
}
