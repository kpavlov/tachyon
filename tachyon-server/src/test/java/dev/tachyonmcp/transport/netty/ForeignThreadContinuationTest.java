/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.RpcDispatcher;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies a {@link ToolResult} completed from a foreign thread is re-anchored to the server
 * executor before output validation runs.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ForeignThreadContinuationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void foreignThreadCompletionReanchoredToServerExecutor() throws Exception {
        var capturedThread = new CompletableFuture<String>();

        var outputSchema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode())
                .put("additionalProperties", true);

        var descriptor = ToolDescriptor.builder()
                .name("foreign-thread-tool")
                .description("Returns future completed from a foreign thread")
                .outputSchema(outputSchema)
                .build();

        var handler = ToolHandler.ofAsync(descriptor, (ctx, args) -> {
            var future = new CompletableFuture<ToolResult>();
            var completer = new Thread(
                    () -> future.complete(ToolResult.of(Map.of("result", "from-foreign"), "from-foreign")),
                    "foreign-completer");
            completer.start();
            return future;
        });

        var recordingValidator = (JsonSchemaValidator) (schema, arguments) -> {
            capturedThread.complete(Thread.currentThread().getName() + " virtual:"
                    + Thread.currentThread().isVirtual());
            return java.util.List.of();
        };

        try (ServerEngine server = newEngine(
                b -> b.json(j -> j.outputSchemaValidator(recordingValidator)).tool(handler))) {
            var session = server.createSession("sess-foreign");
            session.activate();
            var dispatcher = new RpcDispatcher(server, server.executor());
            var params = Map.of("name", "foreign-thread-tool", "arguments", Map.of());
            dispatcher
                    .dispatchRequestAsync(1, "tools/call", params, "sess-foreign")
                    .get(10, TimeUnit.SECONDS);
        }

        String threadInfo = capturedThread.get(10, TimeUnit.SECONDS);
        assertThat(threadInfo)
                .as("output validation must run on server executor virtual thread, not foreign completer")
                .endsWith("virtual:true");
        assertThat(threadInfo)
                .as("thread name must start with server executor prefix 'tachyon-'")
                .startsWith("tachyon-");
    }
}
