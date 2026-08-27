/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThatJsonRpcResponse;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.testkit.TestTaskExecutionEngine;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskAugmentedToolTest extends AbstractStatefulMcpE2eTest {

    private TestTaskExecutionEngine taskEngine;
    private TaskSnapshot snapshot;

    @Override
    protected void startDefaultServer() {
        snapshot = TaskSnapshot.working("external-42", Instant.parse("2026-08-27T07:00:00Z"), 1);
        taskEngine = new TestTaskExecutionEngine().publish(snapshot);
        startServer(builder -> builder.capabilities(c -> c.tasks(taskEngine, true, true, true)), registrar -> {
            registrar
                    .tools()
                    .register(
                            b -> b.name("required").taskSupport(TaskSupport.REQUIRED),
                            (context, request) -> ToolResult.task(snapshot));
            registrar
                    .tools()
                    .register(
                            b -> b.name("optional").taskSupport(TaskSupport.OPTIONAL),
                            (context, request) -> ToolResult.text("inline"));
            registrar
                    .tools()
                    .register(
                            b -> b.name("forbidden").taskSupport(TaskSupport.FORBIDDEN),
                            (context, request) -> ToolResult.text("inline"));
        });
    }

    @Test
    void requiredToolRejectsInlineCall() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                      "name":"required","arguments":{}}}
                    """);
            assertThatJsonRpcResponse(response).isJsonRpcError().hasErrorCode(-32602);
        }
    }

    @Test
    void optionalToolMayReturnInlineResult() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                      "name":"optional","arguments":{}}}
                    """);
            assertThatJson(response).inPath("$.result.content[0].text").isEqualTo("inline");
        }
    }

    @Test
    void optionalToolRejectsInlineResultForTaskAugmentedCall() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                      "name":"optional","arguments":{},"task":{}}}
                    """);
            assertThatJsonRpcResponse(response).isJsonRpcError().hasErrorCode(-32603);
        }
    }

    @Test
    void forbiddenToolRejectsTaskAugmentation() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                      "name":"forbidden","arguments":{},"task":{}}}
                    """);
            assertThatJsonRpcResponse(response).isJsonRpcError().hasErrorCode(-32602);
        }
    }
}
